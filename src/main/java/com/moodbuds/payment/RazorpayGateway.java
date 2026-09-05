package com.moodbuds.payment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.moodbuds.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class RazorpayGateway {
    private static final Logger log = LoggerFactory.getLogger(RazorpayGateway.class);
    private final RazorpayProperties properties;
    private final RestClient.Builder restClientBuilder;

    public RazorpayGateway(RazorpayProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClientBuilder = restClientBuilder;
    }

    public ProviderOrder createOrder(long amount, String receipt, long moodbudsOrderId) {
        requireConfigured();
        var body = new LinkedHashMap<String, Object>();
        body.put("amount", amount);
        body.put("currency", "INR");
        body.put("receipt", receipt);
        body.put("partial_payment", false);
        body.put("notes", Map.of("moodbuds_order_id", String.valueOf(moodbudsOrderId),
                "moodbuds_order_number", receipt));
        JsonNode response = call(() -> client().post().uri("/v1/orders").body(body)
                .retrieve().body(JsonNode.class));
        return new ProviderOrder(required(response, "id"), response.path("amount").asLong(),
                required(response, "currency"), required(response, "status"), response);
    }

    public ProviderPayment fetchPayment(String paymentId) {
        requireConfigured();
        JsonNode response = call(() -> client().get().uri("/v1/payments/{id}", paymentId)
                .retrieve().body(JsonNode.class));
        return payment(response);
    }

    public List<ProviderPayment> fetchPaymentsForOrder(String razorpayOrderId) {
        requireConfigured();
        JsonNode response = call(() -> client().get().uri("/v1/orders/{id}/payments", razorpayOrderId)
                .retrieve().body(JsonNode.class));
        var payments = new ArrayList<ProviderPayment>();
        response.path("items").forEach(item -> payments.add(payment(item)));
        return List.copyOf(payments);
    }

    public ProviderRefund createRefund(String paymentId, long amount, String receipt, String reason,
                                       String idempotencyKey, String speed) {
        requireConfigured();
        var body = new LinkedHashMap<String, Object>();
        body.put("amount", amount);
        body.put("speed", speed.toLowerCase(java.util.Locale.ROOT));
        body.put("receipt", receipt);
        body.put("notes", Map.of("moodbuds_refund_receipt", receipt, "reason", reason));
        JsonNode response = call(() -> client().post().uri("/v1/payments/{id}/refund", paymentId)
                .header("X-Refund-Idempotency", idempotencyKey).body(body)
                .retrieve().body(JsonNode.class));
        return refund(response);
    }

    public ProviderRefund fetchRefund(String refundId) {
        requireConfigured();
        JsonNode response = call(() -> client().get().uri("/v1/refunds/{id}", refundId)
                .retrieve().body(JsonNode.class));
        return refund(response);
    }

    public String publicKeyId() { requireConfigured(); return properties.keyId(); }

    private ProviderPayment payment(JsonNode response) {
        return new ProviderPayment(required(response, "id"), required(response, "order_id"),
                response.path("amount").asLong(), required(response, "currency"),
                required(response, "status"), response.path("method").asText(null), response);
    }

    private ProviderRefund refund(JsonNode response) {
        JsonNode acquirer = response.path("acquirer_data");
        String reference = text(acquirer, "arn");
        if (reference == null) reference = text(acquirer, "rrn");
        if (reference == null) reference = text(acquirer, "utr");
        return new ProviderRefund(required(response, "id"), required(response, "payment_id"),
                response.path("amount").asLong(), required(response, "currency"),
                required(response, "status"), text(response, "speed_requested"),
                text(response, "speed_processed"), reference, response);
    }

    private RestClient client() {
        return restClientBuilder.clone().baseUrl(properties.baseUrl())
                .defaultHeaders(headers -> headers.setBasicAuth(properties.keyId(), properties.keySecret())).build();
    }

    private void requireConfigured() {
        if (!properties.configured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "RAZORPAY_NOT_CONFIGURED",
                    "Razorpay test credentials are not configured");
        }
    }

    private <T> T call(java.util.function.Supplier<T> operation) {
        try {
            T result = operation.get();
            if (result == null) throw new RestClientException("Empty Razorpay response");
            return result;
        } catch (RestClientException exception) {
            log.warn("Razorpay API request failed: {}", exception.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "RAZORPAY_API_ERROR",
                    "Razorpay could not process the payment request");
        }
    }

    private static String required(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "RAZORPAY_INVALID_RESPONSE",
                    "Razorpay returned an incomplete response");
        }
        return value;
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    public record ProviderOrder(String id, long amount, String currency, String status, JsonNode raw) {}
    public record ProviderPayment(String id, String orderId, long amount, String currency,
                                  String status, String method, JsonNode raw) {}
    public record ProviderRefund(String id, String paymentId, long amount, String currency,
                                 String status, String speedRequested, String speedProcessed,
                                 String providerReference, JsonNode raw) {}
}
