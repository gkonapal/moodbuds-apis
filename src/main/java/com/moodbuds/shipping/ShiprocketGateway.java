package com.moodbuds.shipping;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.moodbuds.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class ShiprocketGateway implements ShippingGateway {
    private final ShippingProperties properties;
    private final RestClient.Builder restClientBuilder;
    private volatile String token;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;

    public ShiprocketGateway(ShippingProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClientBuilder = restClientBuilder;
    }

    @Override
    public Quote quote(String pickupPincode, String deliveryPincode, int weightGrams,
                       BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm, boolean reverse) {
        requireAvailable();
        if (properties.mockMode()) return mockQuote(deliveryPincode, weightGrams, reverse);
        BigDecimal weightKg = BigDecimal.valueOf(Math.max(weightGrams, 1))
                .divide(BigDecimal.valueOf(1000), 3, RoundingMode.UP);
        JsonNode response = call(() -> client().get().uri(uri -> uri
                .path("/v1/external/courier/serviceability/")
                .queryParam("pickup_postcode", pickupPincode)
                .queryParam("delivery_postcode", deliveryPincode)
                .queryParam("weight", weightKg)
                .queryParam("cod", 0)
                .queryParam("is_return", reverse ? 1 : 0).build()).retrieve().body(JsonNode.class));
        JsonNode companies = response.path("data").path("available_courier_companies");
        if (!companies.isArray() || companies.isEmpty()) {
            return new Quote(false, null, null, 0, null,
                    response.path("message").asText("Delivery is not available for this pincode"), response);
        }
        JsonNode best = null;
        for (JsonNode company : companies) {
            if (best == null || deliveryDays(company) < deliveryDays(best)
                    || (deliveryDays(company) == deliveryDays(best) && rate(company) < rate(best))) best = company;
        }
        return new Quote(true, text(best, "courier_company_id", "id"),
                text(best, "courier_name", "courier_company_name"), rate(best),
                date(text(best, "etd", "estimated_delivery_date"), deliveryDays(best)),
                "Delivery is available", best);
    }

    @Override
    public Booking bookForward(ForwardOrder order, Quote quote) {
        requireAvailable();
        if (properties.mockMode()) return mockBooking(order.orderNumber(), quote, false);
        Map<String, Object> body = baseOrder(order.orderNumber(), order.orderDate(), order.customer(),
                order.items(), order.subtotal(), order.parcel(), false, null);
        JsonNode created = call(() -> client().post().uri("/v1/external/orders/create/adhoc")
                .body(body).retrieve().body(JsonNode.class));
        return assignAndPickup(created, quote, false);
    }

    @Override
    public Booking bookReverse(ReverseOrder order, Quote quote) {
        requireAvailable();
        if (properties.mockMode()) return mockBooking(order.returnNumber(), quote, true);
        Map<String, Object> body = baseOrder(order.returnNumber(), order.orderDate(), order.pickup(),
                order.items(), order.subtotal(), order.parcel(), true, order.warehouse());
        JsonNode created = call(() -> client().post().uri("/v1/external/orders/create/return")
                .body(body).retrieve().body(JsonNode.class));
        return assignAndPickup(created, quote, true);
    }

    @Override
    public Tracking track(String awbCode) {
        requireAvailable();
        if (properties.mockMode()) {
            var raw = JsonNodeFactory.instance.objectNode().put("awb", awbCode).put("status", "IN_TRANSIT");
            return new Tracking("IN_TRANSIT", "IN_TRANSIT", "Shipment is in transit", "Mock sorting hub",
                    LocalDate.now().plusDays(2), raw);
        }
        JsonNode response = call(() -> client().get().uri("/v1/external/courier/track/awb/{awb}", awbCode)
                .retrieve().body(JsonNode.class));
        JsonNode tracking = response.path("tracking_data");
        String providerStatus = text(tracking.path("shipment_track").path(0), "current_status", "status");
        return new Tracking(providerStatus, normalize(providerStatus), tracking.path("track_status").asText(null),
                text(tracking.path("shipment_track").path(0), "current_city", "location"),
                date(text(tracking, "etd", "estimated_delivery_date"), 0), response);
    }

    @Override public boolean mockMode() { return properties.mockMode(); }
    @Override public String providerName() { return properties.mockMode() ? "MOCK" : "SHIPROCKET"; }

    private Quote mockQuote(String pincode, int weightGrams, boolean reverse) {
        boolean valid = pincode != null && pincode.matches("[1-9][0-9]{5}") && !pincode.startsWith("999");
        int days = valid ? 2 + Character.digit(pincode.charAt(5), 10) % 4 + (reverse ? 1 : 0) : 0;
        long charge = valid ? 4900 + Math.max(0, weightGrams - 500) / 500 * 1200L + (reverse ? 1000 : 0) : 0;
        var raw = JsonNodeFactory.instance.objectNode().put("mock", true).put("serviceable", valid)
                .put("delivery_pincode", pincode == null ? "" : pincode).put("reverse", reverse);
        return new Quote(valid, valid ? "MOCK-101" : null, valid ? "MoodBuds Mock Express" : null,
                charge, valid ? LocalDate.now().plusDays(days) : null,
                valid ? "Delivery is available" : "Delivery is not available for this pincode", raw);
    }

    private Booking mockBooking(String reference, Quote quote, boolean reverse) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        var raw = JsonNodeFactory.instance.objectNode().put("mock", true).put("reference", reference);
        return new Booking("MOCK-ORDER-" + suffix, "MOCK-SHIP-" + suffix, quote.courierCompanyId(),
                quote.courierName(), "MOCKAWB" + suffix, "PICKUP_SCHEDULED", "SCHEDULED",
                null, LocalDate.now().plusDays(1),
                quote.estimatedDeliveryDate(), quote.charge(), raw);
    }

    private Map<String, Object> baseOrder(String number, LocalDate date, Contact contact,
                                          List<LineItem> items, long subtotal, Parcel parcel,
                                          boolean reverse, Contact warehouse) {
        var body = new LinkedHashMap<String, Object>();
        body.put("order_id", number);
        body.put("order_date", date + " 00:00");
        body.put("pickup_location", properties.pickupLocation());
        if (properties.channelId() != null) body.put("channel_id", properties.channelId());
        body.put("billing_customer_name", contact.firstName());
        body.put("billing_last_name", contact.lastName());
        body.put("billing_address", contact.address());
        body.put("billing_address_2", contact.address2());
        body.put("billing_city", contact.city());
        body.put("billing_pincode", contact.pincode());
        body.put("billing_state", contact.state());
        body.put("billing_country", contact.country());
        body.put("billing_email", contact.email());
        body.put("billing_phone", contact.phone());
        body.put("shipping_is_billing", true);
        body.put("order_items", items.stream().map(item -> Map.of("name", item.name(), "sku", item.sku(),
                "units", item.units(), "selling_price", rupees(item.sellingPrice()))).toList());
        body.put("payment_method", "Prepaid");
        body.put("sub_total", rupees(subtotal));
        body.put("length", parcel.lengthCm());
        body.put("breadth", parcel.widthCm());
        body.put("height", parcel.heightCm());
        body.put("weight", BigDecimal.valueOf(parcel.weightGrams()).divide(BigDecimal.valueOf(1000), 3, RoundingMode.UP));
        if (reverse && warehouse != null) {
            body.put("shipping_customer_name", warehouse.firstName());
            body.put("shipping_last_name", warehouse.lastName());
            body.put("shipping_address", warehouse.address());
            body.put("shipping_address_2", warehouse.address2());
            body.put("shipping_city", warehouse.city());
            body.put("shipping_pincode", warehouse.pincode());
            body.put("shipping_state", warehouse.state());
            body.put("shipping_country", warehouse.country());
            body.put("shipping_email", warehouse.email());
            body.put("shipping_phone", warehouse.phone());
        }
        return body;
    }

    private Booking assignAndPickup(JsonNode created, Quote quote, boolean reverse) {
        String orderId = text(created, "order_id");
        String shipmentId = text(created, "shipment_id");
        if (shipmentId == null) throw providerError("SHIPROCKET_BOOKING_INVALID", "Shiprocket did not return a shipment id");
        var awbBody = new LinkedHashMap<String, Object>();
        awbBody.put("shipment_id", Long.parseLong(shipmentId));
        if (quote.courierCompanyId() != null) awbBody.put("courier_id", Long.parseLong(quote.courierCompanyId()));
        JsonNode awb = call(() -> client().post().uri("/v1/external/courier/assign/awb")
                .body(awbBody).retrieve().body(JsonNode.class));
        String awbCode = text(awb.path("response").path("data"), "awb_code");
        String courier = text(awb.path("response").path("data"), "courier_name", "courier_company_name");
        JsonNode pickup = call(() -> client().post().uri("/v1/external/courier/generate/pickup")
                .body(Map.of("shipment_id", List.of(Long.parseLong(shipmentId))))
                .retrieve().body(JsonNode.class));
        var raw = JsonNodeFactory.instance.objectNode(); raw.set("order", created); raw.set("awb", awb); raw.set("pickup", pickup);
        return new Booking(orderId, shipmentId, quote.courierCompanyId(),
                courier == null ? quote.courierName() : courier, awbCode, "PICKUP_SCHEDULED", "SCHEDULED",
                null,
                LocalDate.now().plusDays(1), quote.estimatedDeliveryDate(), quote.charge(), raw);
    }

    private RestClient client() {
        return restClientBuilder.baseUrl(properties.baseUrl()).defaultHeader("Authorization", "Bearer " + token()).build();
    }

    private synchronized String token() {
        if (token != null && tokenExpiresAt.isAfter(Instant.now().plusSeconds(60))) return token;
        JsonNode response;
        try {
            response = restClientBuilder.baseUrl(properties.baseUrl()).build().post()
                    .uri("/v1/external/auth/login")
                    .body(Map.of("email", properties.apiEmail(), "password", properties.apiPassword()))
                    .retrieve().body(JsonNode.class);
        } catch (RestClientException exception) {
            throw providerError("SHIPROCKET_AUTH_FAILED", "Could not authenticate with Shiprocket");
        }
        token = response == null ? null : response.path("token").asText(null);
        if (token == null || token.isBlank()) throw providerError("SHIPROCKET_AUTH_FAILED", "Shiprocket did not return an access token");
        tokenExpiresAt = Instant.now().plusSeconds(9 * 24 * 60 * 60L);
        return token;
    }

    private <T> T call(java.util.concurrent.Callable<T> action) {
        try { return action.call(); }
        catch (ApiException exception) { throw exception; }
        catch (Exception exception) { throw providerError("SHIPROCKET_UNAVAILABLE", "Shiprocket is temporarily unavailable"); }
    }

    private void requireAvailable() {
        if (!properties.available()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "SHIPPING_PROVIDER_UNAVAILABLE", "Shipping is not configured");
    }
    private ApiException providerError(String code, String message) {
        return new ApiException(HttpStatus.BAD_GATEWAY, code, message);
    }
    private static long rate(JsonNode node) {
        return node.path("rate").decimalValue().multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();
    }
    private static int deliveryDays(JsonNode node) {
        int value = node.path("estimated_delivery_days").asInt(node.path("etd_hours").asInt(96) / 24);
        return value <= 0 ? 4 : value;
    }
    private static LocalDate date(String value, int fallbackDays) {
        if (value != null && !value.isBlank()) {
            for (DateTimeFormatter formatter : List.of(DateTimeFormatter.ISO_LOCAL_DATE,
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"), DateTimeFormatter.ofPattern("dd-MM-yyyy"))) {
                try { return LocalDate.parse(value.length() > 10 && formatter == DateTimeFormatter.ISO_LOCAL_DATE ? value.substring(0, 10) : value, formatter); }
                catch (DateTimeParseException ignored) { }
            }
        }
        return fallbackDays > 0 ? LocalDate.now().plusDays(fallbackDays) : null;
    }
    private static String text(JsonNode node, String... fields) {
        if (node == null) return null;
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (!value.isMissingNode() && !value.isNull() && !value.asText().isBlank()) return value.asText();
        }
        return null;
    }
    private static BigDecimal rupees(long paise) { return BigDecimal.valueOf(paise, 2); }
    static String normalize(String status) {
        String value = status == null ? "UNKNOWN" : status.toUpperCase(Locale.ROOT).replace(' ', '_');
        if (value.contains("OUT_FOR_DELIVERY")) return "OUT_FOR_DELIVERY";
        if (value.contains("DELIVERED")) return "DELIVERED";
        if (value.contains("PICKED") || value.contains("IN_TRANSIT") || value.contains("SHIPPED")) return "IN_TRANSIT";
        if (value.contains("PICKUP") && value.contains("SCHEDULE")) return "PICKUP_SCHEDULED";
        if (value.contains("CANCEL")) return "CANCELLED";
        if (value.contains("FAIL") || value.contains("ERROR") || value.contains("NDR")) return "EXCEPTION";
        return value;
    }
}
