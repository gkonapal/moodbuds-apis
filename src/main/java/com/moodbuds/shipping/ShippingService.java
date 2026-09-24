package com.moodbuds.shipping;

import static com.moodbuds.shipping.ShippingDtos.*;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodbuds.common.ApiException;
import com.moodbuds.common.PageResponse;
import com.moodbuds.customer.CustomerProfileService;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class ShippingService {
    private final JdbcClient jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final ObjectMapper objectMapper;
    private final CustomerProfileService customers;
    private final ShippingProperties properties;
    private final ShippingGateway gateway;

    public ShippingService(JdbcClient jdbc, NamedParameterJdbcTemplate namedJdbc, ObjectMapper objectMapper,
                           CustomerProfileService customers, ShippingProperties properties,
                           ShippingGateway gateway) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
        this.objectMapper = objectMapper;
        this.customers = customers;
        this.properties = properties;
        this.gateway = gateway;
    }

    @Transactional
    public ShippingQuoteResponse quoteCart(long customerId, long addressId) {
        if (!properties.available()) throw unavailable();
        var address = customers.address(customerId, addressId);
        PackageRow parcel = cartParcel(customerId);
        ShippingGateway.Quote quote = gateway.quote(properties.originPincode(), address.pincode(),
                parcel.weightGrams(), parcel.lengthCm(), parcel.widthCm(), parcel.heightCm(), false);
        long customerCost = customerCost(quote.charge());
        Instant expiresAt = Instant.now().plus(properties.quoteValidity());
        var keys = new GeneratedKeyHolder();
        namedJdbc.update("""
                INSERT INTO shipping_quotes(user_id,address_id,provider,courier_company_id,courier_name,
                    delivery_pincode,weight_grams,shipping_charge,estimated_delivery_date,serviceable,
                    expires_at,created_at)
                VALUES(:userId,:addressId,:provider,:courierId,:courier,:pincode,:weight,:charge,:eta,
                    :serviceable,:expiresAt,UTC_TIMESTAMP())
                """, new MapSqlParameterSource().addValue("userId", customerId).addValue("addressId", addressId)
                .addValue("provider", gateway.providerName()).addValue("courierId", quote.courierCompanyId())
                .addValue("courier", quote.courierName()).addValue("pincode", address.pincode())
                .addValue("weight", parcel.weightGrams()).addValue("charge", quote.charge())
                .addValue("eta", quote.estimatedDeliveryDate(), Types.DATE)
                .addValue("serviceable", quote.serviceable()).addValue("expiresAt", expiresAt),
                keys, new String[]{"id"});
        return new ShippingQuoteResponse(keys.getKey().longValue(), quote.serviceable(),
                gateway.mockMode() ? "MOCK" : "SHIPROCKET", quote.courierCompanyId(), quote.courierName(),
                quote.message(), address.pincode(), parcel.weightGrams(), quote.charge(), customerCost,
                quote.estimatedDeliveryDate(), expiresAt);
    }

    public ShippingQuoteResponse requireFreshQuote(long customerId, long addressId) {
        ShippingQuoteResponse quote = quoteCart(customerId, addressId);
        if (!quote.serviceable()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                "PINCODE_NOT_SERVICEABLE", quote.message());
        return quote;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void orderConfirmed(OrderConfirmedEvent event) {
        try { bookForward(event.orderId()); }
        catch (RuntimeException exception) { markForwardFailure(event.orderId(), exception.getMessage()); }
    }

    @Transactional
    public ShipmentResponse bookForwardByOrderNumber(String orderNumber) {
        long orderId = jdbc.sql("SELECT id FROM orders WHERE order_number=:number")
                .param("number", orderNumber).query(Long.class).optional()
                .orElseThrow(() -> ApiException.notFound("Order"));
        return bookForward(orderId);
    }

    @Transactional
    public ShipmentResponse bookForward(long orderId) {
        ShipmentRow existing = shipmentFor(orderId, null, "FORWARD", true);
        if (existing != null && existing.awbCode() != null) return response(existing);
        ForwardRow order = forwardOrder(orderId);
        if (!List.of("CONFIRMED", "PACKED").contains(order.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_NOT_READY_FOR_SHIPPING",
                    "Only confirmed or packed orders can be booked for shipping");
        }
        ShippingGateway.Quote providerQuote;
        if (order.shippingQuoteId() == null) {
            String deliveryPincode = order.address().path("pincode").asText();
            providerQuote = gateway.quote(properties.originPincode(), deliveryPincode,
                    order.parcel().weightGrams(), order.parcel().lengthCm(), order.parcel().widthCm(),
                    order.parcel().heightCm(), false);
            if (!providerQuote.serviceable()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "PINCODE_NOT_SERVICEABLE", providerQuote.message());
            jdbc.sql("""
                    UPDATE orders SET expected_delivery_date=:eta,
                        shipping_pricing_source=CONCAT(:provider,'_LEGACY_ORDER'),updated_at=UTC_TIMESTAMP()
                    WHERE id=:id
                    """).param("eta", providerQuote.estimatedDeliveryDate(), Types.DATE)
                    .param("provider", gateway.providerName()).param("id", orderId).update();
        } else {
            QuoteRow storedQuote = quote(order.shippingQuoteId());
            providerQuote = new ShippingGateway.Quote(true, storedQuote.courierCompanyId(),
                    storedQuote.courierName(), storedQuote.providerCharge(), storedQuote.estimatedDeliveryDate(),
                    "Stored checkout quote", null);
        }
        long shipmentId = existing == null ? insertPending(orderId, null, "FORWARD") : existing.id();
        ShippingGateway.Booking booking = gateway.bookForward(toForwardOrder(order), providerQuote);
        applyBooking(shipmentId, booking);
        return response(requireShipment(shipmentId));
    }

    @Transactional
    public ShipmentResponse scheduleReverse(long returnRequestId) {
        ReverseRow row = reverseOrder(returnRequestId);
        ShipmentRow existing = shipmentFor(row.orderId(), returnRequestId, "REVERSE", true);
        if (existing != null && existing.awbCode() != null) return response(existing);
        PackageRow parcel = returnParcel(returnRequestId);
        ShippingGateway.Quote quote = gateway.quote(row.pickup().path("pincode").asText(),
                properties.originPincode(), parcel.weightGrams(), parcel.lengthCm(), parcel.widthCm(),
                parcel.heightCm(), true);
        if (!quote.serviceable()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                "RETURN_PINCODE_NOT_SERVICEABLE", quote.message());
        long shipmentId = existing == null ? insertPending(row.orderId(), returnRequestId, "REVERSE") : existing.id();
        ShippingGateway.Booking booking = gateway.bookReverse(toReverseOrder(row, parcel), quote);
        applyBooking(shipmentId, booking);
        jdbc.sql("""
                UPDATE return_requests SET expected_pickup_date=:pickup,
                    expected_warehouse_arrival_date=:delivery,updated_at=UTC_TIMESTAMP() WHERE id=:id
                """).param("pickup", booking.estimatedPickupDate(), Types.DATE)
                .param("delivery", booking.estimatedDeliveryDate(), Types.DATE)
                .param("id", returnRequestId).update();
        return response(requireShipment(shipmentId));
    }

    public PageResponse<ShipmentResponse> list(String direction, String status, int page, int size) {
        int boundedSize = Math.min(Math.max(size, 1), 100), boundedPage = Math.max(page, 0);
        var clauses = new ArrayList<String>();
        if (direction != null && !direction.isBlank()) clauses.add("s.direction=:direction");
        if (status != null && !status.isBlank()) clauses.add("s.status=:status");
        String where = clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses);
        var query = jdbc.sql(shipmentSql() + where + " ORDER BY s.id DESC LIMIT :limit OFFSET :offset")
                .param("limit", boundedSize).param("offset", boundedPage * boundedSize);
        var count = jdbc.sql("SELECT COUNT(*) FROM shipments s" + where);
        if (direction != null && !direction.isBlank()) {
            query = query.param("direction", direction.toUpperCase(Locale.ROOT));
            count = count.param("direction", direction.toUpperCase(Locale.ROOT));
        }
        if (status != null && !status.isBlank()) {
            query = query.param("status", status.toUpperCase(Locale.ROOT));
            count = count.param("status", status.toUpperCase(Locale.ROOT));
        }
        return PageResponse.of(query.query((rs, n) -> response(shipment(rs))).list(), boundedPage, boundedSize,
                count.query(Long.class).single());
    }

    public ShipmentResponse forOrder(long customerId, String orderNumber) {
        ShipmentRow row = jdbc.sql(shipmentSql() + " WHERE o.user_id=:userId AND o.order_number=:number AND s.direction='FORWARD' ORDER BY s.id DESC LIMIT 1")
                .param("userId", customerId).param("number", orderNumber)
                .query((rs, n) -> shipment(rs)).optional().orElse(null);
        return row == null ? null : response(row);
    }

    public ShipmentResponse forOrderAdmin(String orderNumber) {
        ShipmentRow row = jdbc.sql(shipmentSql() + " WHERE o.order_number=:number AND s.direction='FORWARD' ORDER BY s.id DESC LIMIT 1")
                .param("number", orderNumber).query((rs, n) -> shipment(rs)).optional().orElse(null);
        return row == null ? null : response(row);
    }

    public ShipmentResponse forReturn(long customerId, long returnId) {
        ShipmentRow row = jdbc.sql(shipmentSql() + " WHERE o.user_id=:userId AND s.return_request_id=:returnId AND s.direction='REVERSE' ORDER BY s.id DESC LIMIT 1")
                .param("userId", customerId).param("returnId", returnId)
                .query((rs, n) -> shipment(rs)).optional().orElse(null);
        return row == null ? null : response(row);
    }

    public ShipmentResponse forReturnAdmin(long returnId) {
        ShipmentRow row = jdbc.sql(shipmentSql() + " WHERE s.return_request_id=:returnId AND s.direction='REVERSE' ORDER BY s.id DESC LIMIT 1")
                .param("returnId", returnId).query((rs, n) -> shipment(rs)).optional().orElse(null);
        return row == null ? null : response(row);
    }

    @Transactional
    public ShipmentResponse mockUpdate(long shipmentId, MockShipmentUpdateRequest request) {
        if (!gateway.mockMode()) throw new ApiException(HttpStatus.NOT_FOUND, "MOCK_SHIPPING_DISABLED",
                "Mock shipment controls are disabled");
        ShipmentRow row = requireShipment(shipmentId);
        String status = ShiprocketGateway.normalize(request.status());
        jdbc.sql("""
                UPDATE shipments SET status=:status,last_location=:location,
                    estimated_pickup_date=COALESCE(:pickup,estimated_pickup_date),
                    estimated_delivery_date=COALESCE(:delivery,estimated_delivery_date),updated_at=UTC_TIMESTAMP()
                WHERE id=:id
                """).param("status", status).param("location", request.location())
                .param("pickup", request.estimatedPickupDate(), Types.DATE)
                .param("delivery", request.estimatedDeliveryDate(), Types.DATE).param("id", shipmentId).update();
        addEvent(shipmentId, "mock:" + shipmentId + ":" + status + ":" + Instant.now().toEpochMilli(),
                request.status(), status, request.message(), request.location(), Instant.now(), null);
        syncBusinessStatus(row.orderId(), row.returnRequestId(), row.direction(), status, request.message());
        return response(requireShipment(shipmentId));
    }

    @Transactional
    public void processWebhook(String token, String rawBody) {
        if (properties.webhookToken() == null || properties.webhookToken().isBlank()
                || !constantTime(properties.webhookToken(), token)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_SHIPPING_WEBHOOK_TOKEN",
                    "Shipping webhook authentication failed");
        }
        JsonNode payload;
        try { payload = objectMapper.readTree(rawBody); }
        catch (JsonProcessingException exception) { throw ApiException.badRequest("INVALID_WEBHOOK_PAYLOAD", "Invalid shipping webhook payload"); }
        String awb = first(payload, "awb", "awb_code", "awbCode");
        String providerStatus = first(payload, "current_status", "shipment_status", "status");
        if (awb == null || providerStatus == null) throw ApiException.badRequest("INVALID_WEBHOOK_PAYLOAD", "AWB and status are required");
        ShipmentRow row = jdbc.sql(shipmentSql() + " WHERE s.awb_code=:awb FOR UPDATE").param("awb", awb)
                .query((rs, n) -> shipment(rs)).optional().orElseThrow(() -> ApiException.notFound("Shipment"));
        String normalized = ShiprocketGateway.normalize(providerStatus);
        Instant eventAt = Instant.now();
        String key = eventKey(awb + "|" + providerStatus + "|" + payload.path("timestamp").asText("") + "|" + rawBody.hashCode());
        int inserted = addEvent(row.id(), key, providerStatus, normalized,
                first(payload, "activity", "message"), first(payload, "current_city", "location"), eventAt, rawBody);
        if (inserted == 0) return;
        LocalDate eta = parseDate(first(payload, "etd", "estimated_delivery_date"));
        jdbc.sql("""
                UPDATE shipments SET status=:status,last_location=:location,
                    estimated_delivery_date=COALESCE(:eta,estimated_delivery_date),provider_response=:payload,
                    updated_at=UTC_TIMESTAMP() WHERE id=:id
                """).param("status", normalized).param("location", first(payload, "current_city", "location"))
                .param("eta", eta, Types.DATE).param("payload", rawBody).param("id", row.id()).update();
        syncBusinessStatus(row.orderId(), row.returnRequestId(), row.direction(), normalized,
                first(payload, "activity", "message"));
    }

    private int addEvent(long shipmentId, String key, String providerStatus, String normalized, String message,
                         String location, Instant eventAt, String payload) {
        return jdbc.sql("""
                INSERT IGNORE INTO shipment_events(shipment_id,provider_event_key,provider_status,
                    normalized_status,message,location,event_at,payload,received_at)
                VALUES(:shipmentId,:key,:providerStatus,:normalized,:message,:location,:eventAt,:payload,UTC_TIMESTAMP())
                """).param("shipmentId", shipmentId).param("key", key).param("providerStatus", providerStatus)
                .param("normalized", normalized).param("message", message).param("location", location)
                .param("eventAt", eventAt).param("payload", payload, Types.VARCHAR).update();
    }

    private void syncBusinessStatus(long orderId, Long returnId, String direction, String status, String message) {
        if ("FORWARD".equals(direction)) {
            String target = switch (status) {
                case "IN_TRANSIT" -> "SHIPPED";
                case "OUT_FOR_DELIVERY" -> "OUT_FOR_DELIVERY";
                case "DELIVERED" -> "DELIVERED";
                default -> null;
            };
            if (target != null) advanceOrder(orderId, target, message == null ? "Updated by shipping provider" : message);
        } else if (returnId != null && "DELIVERED".equals(status)) {
            String current = jdbc.sql("SELECT status FROM return_requests WHERE id=:id FOR UPDATE")
                    .param("id", returnId).query(String.class).single();
            if ("PICKUP_SCHEDULED".equals(current)) {
                jdbc.sql("UPDATE return_requests SET status='ITEM_RECEIVED',updated_at=UTC_TIMESTAMP() WHERE id=:id")
                        .param("id", returnId).update();
                jdbc.sql("""
                        INSERT INTO return_status_history(return_request_id,from_status,to_status,notes,actor_type,created_at)
                        VALUES(:id,'PICKUP_SCHEDULED','ITEM_RECEIVED',:notes,'SYSTEM',UTC_TIMESTAMP())
                        """).param("id", returnId).param("notes", message == null ? "Reverse shipment delivered to warehouse" : message).update();
                advanceOrder(orderId, "RETURN_RECEIVED", "Reverse shipment delivered to warehouse");
            }
        }
    }

    private void advanceOrder(long orderId, String target, String notes) {
        String current = jdbc.sql("SELECT status FROM orders WHERE id=:id FOR UPDATE").param("id", orderId)
                .query(String.class).single();
        List<String> progression = List.of("CONFIRMED", "PACKED", "SHIPPED", "OUT_FOR_DELIVERY", "DELIVERED");
        if (target.startsWith("RETURN_")) {
            if (!target.equals(current)) {
                jdbc.sql("UPDATE orders SET status=:target,updated_at=UTC_TIMESTAMP() WHERE id=:id")
                        .param("target", target).param("id", orderId).update();
                history(orderId, current, target, notes);
            }
            return;
        }
        int currentIndex = progression.indexOf(current), targetIndex = progression.indexOf(target);
        if (targetIndex <= currentIndex || currentIndex < 0) return;
        jdbc.sql("UPDATE orders SET status=:target,updated_at=UTC_TIMESTAMP() WHERE id=:id")
                .param("target", target).param("id", orderId).update();
        history(orderId, current, target, notes);
    }

    private void history(long orderId, String from, String to, String notes) {
        jdbc.sql("""
                INSERT INTO order_status_history(order_id,from_status,to_status,notes,changed_by_id,created_at)
                VALUES(:id,:from,:to,:notes,NULL,UTC_TIMESTAMP())
                """).param("id", orderId).param("from", from).param("to", to).param("notes", notes).update();
    }

    private long insertPending(long orderId, Long returnId, String direction) {
        var keys = new GeneratedKeyHolder();
        namedJdbc.update("""
                INSERT INTO shipments(order_id,return_request_id,direction,provider,status,created_at,updated_at)
                VALUES(:orderId,:returnId,:direction,:provider,'BOOKING_PENDING',UTC_TIMESTAMP(),UTC_TIMESTAMP())
                """, new MapSqlParameterSource().addValue("orderId", orderId)
                .addValue("returnId", returnId, Types.BIGINT).addValue("direction", direction)
                .addValue("provider", gateway.providerName()), keys, new String[]{"id"});
        return keys.getKey().longValue();
    }

    private void applyBooking(long shipmentId, ShippingGateway.Booking booking) {
        jdbc.sql("""
                UPDATE shipments SET provider_order_id=:providerOrderId,provider_shipment_id=:providerShipmentId,
                    courier_company_id=:courierId,courier_name=:courier,awb_code=:awb,status=:status,
                    pickup_status=:pickupStatus,tracking_url=:trackingUrl,shipping_charge=:charge,
                    estimated_pickup_date=:pickupDate,estimated_delivery_date=:deliveryDate,
                    failure_code=NULL,failure_description=NULL,provider_response=:response,updated_at=UTC_TIMESTAMP()
                WHERE id=:id
                """).param("providerOrderId", booking.providerOrderId()).param("providerShipmentId", booking.providerShipmentId())
                .param("courierId", booking.courierCompanyId()).param("courier", booking.courierName())
                .param("awb", booking.awbCode()).param("status", booking.status()).param("pickupStatus", booking.pickupStatus())
                .param("trackingUrl", booking.trackingUrl()).param("charge", booking.charge())
                .param("pickupDate", booking.estimatedPickupDate(), Types.DATE)
                .param("deliveryDate", booking.estimatedDeliveryDate(), Types.DATE)
                .param("response", json(booking.raw())).param("id", shipmentId).update();
        addEvent(shipmentId, "booking:" + shipmentId, booking.status(), booking.status(),
                "Courier booked and pickup scheduled", null, Instant.now(), json(booking.raw()));
    }

    private void markForwardFailure(long orderId, String message) {
        ShipmentRow row = shipmentFor(orderId, null, "FORWARD", false);
        if (row == null) {
            long id = insertPending(orderId, null, "FORWARD");
            row = requireShipment(id);
        }
        jdbc.sql("""
                UPDATE shipments SET status='BOOKING_FAILED',failure_code='PROVIDER_ERROR',
                    failure_description=:message,updated_at=UTC_TIMESTAMP() WHERE id=:id
                """).param("message", message == null ? "Shipping booking failed" : message).param("id", row.id()).update();
    }

    private PackageRow cartParcel(long customerId) {
        return jdbc.sql("""
                SELECT COALESCE(SUM(COALESCE(p.weight_grams,:weight)*ci.quantity),0) weight_grams,
                       COALESCE(MAX(COALESCE(p.length_cm,:length)),:length) length_cm,
                       COALESCE(MAX(COALESCE(p.width_cm,:width)),:width) width_cm,
                       COALESCE(SUM(COALESCE(p.height_cm,:height)*ci.quantity),:height) height_cm,
                       COUNT(*) item_count
                FROM carts c JOIN cart_items ci ON ci.cart_id=c.id JOIN products p ON p.id=ci.product_id
                WHERE c.user_id=:userId AND c.id=(SELECT MAX(c2.id) FROM carts c2 WHERE c2.user_id=:userId)
                """).param("userId", customerId).param("weight", properties.defaultWeightGrams())
                .param("length", properties.defaultLengthCm()).param("width", properties.defaultWidthCm())
                .param("height", properties.defaultHeightCm()).query((rs, n) -> packageRow(rs)).single();
    }

    private PackageRow returnParcel(long returnId) {
        return jdbc.sql("""
                SELECT COALESCE(SUM(COALESCE(p.weight_grams,:weight)*ri.quantity_to_return),0) weight_grams,
                       COALESCE(MAX(COALESCE(p.length_cm,:length)),:length) length_cm,
                       COALESCE(MAX(COALESCE(p.width_cm,:width)),:width) width_cm,
                       COALESCE(SUM(COALESCE(p.height_cm,:height)*ri.quantity_to_return),:height) height_cm,
                       COUNT(*) item_count
                FROM return_items ri JOIN order_items oi ON oi.id=ri.order_item_id JOIN products p ON p.id=oi.product_id
                WHERE ri.return_request_id=:returnId
                """).param("returnId", returnId).param("weight", properties.defaultWeightGrams())
                .param("length", properties.defaultLengthCm()).param("width", properties.defaultWidthCm())
                .param("height", properties.defaultHeightCm()).query((rs, n) -> packageRow(rs)).single();
    }

    private PackageRow packageRow(ResultSet rs) throws SQLException {
        if (rs.getInt("item_count") == 0) throw ApiException.badRequest("EMPTY_SHIPMENT", "Shipment has no items");
        return new PackageRow(Math.max(rs.getInt("weight_grams"), properties.defaultWeightGrams()),
                rs.getBigDecimal("length_cm"), rs.getBigDecimal("width_cm"), rs.getBigDecimal("height_cm"));
    }

    private ForwardRow forwardOrder(long orderId) {
        ForwardRow base = jdbc.sql("""
                SELECT o.id,o.order_number,o.status,o.shipping_address_snapshot_full,o.subtotal,o.created_at,
                       o.shipping_quote_id,u.email,u.mobile
                FROM orders o JOIN users u ON u.id=o.user_id WHERE o.id=:id FOR UPDATE
                """).param("id", orderId).query((rs, n) -> new ForwardRow(rs.getLong("id"), rs.getString("order_number"),
                        rs.getString("status"), tree(rs.getString("shipping_address_snapshot_full")),
                        rs.getLong("subtotal"), rs.getTimestamp("created_at").toLocalDateTime().toLocalDate(),
                        nullableLong(rs, "shipping_quote_id"), rs.getString("email"), rs.getString("mobile"), List.of(), null))
                .optional().orElseThrow(() -> ApiException.notFound("Order"));
        List<ShippingGateway.LineItem> items = shipmentItems(orderId, null);
        PackageRow parcel = orderParcel(orderId);
        return new ForwardRow(base.id(), base.orderNumber(), base.status(), base.address(), base.subtotal(),
                base.orderDate(), base.shippingQuoteId(), base.email(), base.mobile(), items, parcel);
    }

    private PackageRow orderParcel(long orderId) {
        return jdbc.sql("""
                SELECT COALESCE(SUM(COALESCE(p.weight_grams,:weight)*oi.quantity),0) weight_grams,
                       COALESCE(MAX(COALESCE(p.length_cm,:length)),:length) length_cm,
                       COALESCE(MAX(COALESCE(p.width_cm,:width)),:width) width_cm,
                       COALESCE(SUM(COALESCE(p.height_cm,:height)*oi.quantity),:height) height_cm,COUNT(*) item_count
                FROM order_items oi JOIN products p ON p.id=oi.product_id WHERE oi.order_id=:orderId
                """).param("orderId", orderId).param("weight", properties.defaultWeightGrams())
                .param("length", properties.defaultLengthCm()).param("width", properties.defaultWidthCm())
                .param("height", properties.defaultHeightCm()).query((rs, n) -> packageRow(rs)).single();
    }

    private ReverseRow reverseOrder(long returnId) {
        return jdbc.sql("""
                SELECT rr.id,rr.order_id,rr.status,rr.pickup_address_snapshot_full,o.order_number,
                       o.subtotal,rr.requested_at,u.email,u.mobile
                FROM return_requests rr JOIN orders o ON o.id=rr.order_id JOIN users u ON u.id=rr.user_id
                WHERE rr.id=:id FOR UPDATE
                """).param("id", returnId).query((rs, n) -> new ReverseRow(rs.getLong("id"), rs.getLong("order_id"),
                        rs.getString("order_number"), rs.getString("status"),
                        tree(rs.getString("pickup_address_snapshot_full")), rs.getLong("subtotal"),
                        rs.getTimestamp("requested_at").toLocalDateTime().toLocalDate(), rs.getString("email"),
                        rs.getString("mobile"), shipmentItems(rs.getLong("order_id"), returnId)))
                .optional().orElseThrow(() -> ApiException.notFound("Return request"));
    }

    private List<ShippingGateway.LineItem> shipmentItems(long orderId, Long returnId) {
        String sql = returnId == null ? """
                SELECT oi.product_snapshot,oi.quantity,oi.line_total FROM order_items oi WHERE oi.order_id=:orderId ORDER BY oi.id
                """ : """
                SELECT oi.product_snapshot,ri.quantity_to_return quantity,
                       ROUND(oi.line_total*ri.quantity_to_return/oi.quantity) line_total
                FROM return_items ri JOIN order_items oi ON oi.id=ri.order_item_id
                WHERE ri.return_request_id=:returnId ORDER BY ri.id
                """;
        var query = jdbc.sql(sql).param("orderId", orderId);
        if (returnId != null) query = query.param("returnId", returnId);
        return query.query((rs, n) -> {
            JsonNode snapshot = tree(rs.getString("product_snapshot"));
            int units = rs.getInt("quantity");
            return new ShippingGateway.LineItem(snapshot.path("name").asText("MoodBuds product"),
                    snapshot.path("sku").asText("MB-ITEM"), units, rs.getLong("line_total") / Math.max(units, 1));
        }).list();
    }

    private ShippingGateway.ForwardOrder toForwardOrder(ForwardRow row) {
        return new ShippingGateway.ForwardOrder(row.id(), row.orderNumber(), row.orderDate(),
                contact(row.address(), row.email(), row.mobile()), row.items(), row.subtotal(),
                new ShippingGateway.Parcel(row.parcel().weightGrams(), row.parcel().lengthCm(),
                        row.parcel().widthCm(), row.parcel().heightCm()));
    }

    private ShippingGateway.ReverseOrder toReverseOrder(ReverseRow row, PackageRow parcel) {
        var warehouse = new ShippingGateway.Contact(properties.warehouseName(), "", properties.warehouseEmail(),
                properties.warehousePhone(), properties.warehouseAddress(), "", properties.warehouseCity(),
                properties.warehouseState(), properties.warehouseCountry(), properties.originPincode());
        return new ShippingGateway.ReverseOrder(row.orderId(), row.id(), "RET-" + row.id(), row.requestedAt(),
                contact(row.pickup(), row.email(), row.mobile()), warehouse, row.items(), row.subtotal(),
                new ShippingGateway.Parcel(parcel.weightGrams(), parcel.lengthCm(), parcel.widthCm(), parcel.heightCm()));
    }

    private ShippingGateway.Contact contact(JsonNode address, String email, String mobile) {
        String fullName = address.path("fullName").asText("Customer").trim();
        int split = fullName.indexOf(' ');
        String first = split < 0 ? fullName : fullName.substring(0, split);
        String last = split < 0 ? "" : fullName.substring(split + 1);
        return new ShippingGateway.Contact(first, last, email,
                address.path("mobile").asText(mobile), address.path("addressLine1").asText(),
                address.path("addressLine2").asText(""), address.path("city").asText(),
                address.path("state").asText(), address.path("country").asText("India"),
                address.path("pincode").asText());
    }

    private QuoteRow quote(Long id) {
        if (id == null) throw new ApiException(HttpStatus.CONFLICT, "SHIPPING_QUOTE_MISSING", "Order has no shipping quote");
        return jdbc.sql("""
                SELECT courier_company_id,courier_name,shipping_charge,estimated_delivery_date,serviceable
                FROM shipping_quotes WHERE id=:id
                """).param("id", id).query((rs, n) -> new QuoteRow(rs.getString("courier_company_id"),
                        rs.getString("courier_name"), rs.getLong("shipping_charge"),
                        nullableDate(rs, "estimated_delivery_date"), rs.getBoolean("serviceable")))
                .optional().filter(QuoteRow::serviceable)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "SHIPPING_QUOTE_INVALID", "Order shipping quote is invalid"));
    }

    private long customerCost(long providerCharge) {
        if (properties.providerRatePricing()) return providerCharge;
        if (properties.flatPricing()) return properties.flatRate();
        return 0;
    }

    private ShipmentRow shipmentFor(long orderId, Long returnId, String direction, boolean lock) {
        String returnClause = returnId == null ? "s.return_request_id IS NULL" : "s.return_request_id=:returnId";
        var query = jdbc.sql(shipmentSql() + " WHERE s.order_id=:orderId AND s.direction=:direction AND " + returnClause
                + " ORDER BY s.id DESC LIMIT 1" + (lock ? " FOR UPDATE" : ""))
                .param("orderId", orderId).param("direction", direction);
        if (returnId != null) query = query.param("returnId", returnId);
        return query.query((rs, n) -> shipment(rs)).optional().orElse(null);
    }

    private ShipmentRow requireShipment(long id) {
        return jdbc.sql(shipmentSql() + " WHERE s.id=:id").param("id", id)
                .query((rs, n) -> shipment(rs)).optional().orElseThrow(() -> ApiException.notFound("Shipment"));
    }

    private String shipmentSql() {
        return """
                SELECT s.*,o.order_number FROM shipments s JOIN orders o ON o.id=s.order_id
                """;
    }

    private ShipmentRow shipment(ResultSet rs) throws SQLException {
        return new ShipmentRow(rs.getLong("id"), rs.getLong("order_id"), rs.getString("order_number"),
                nullableLong(rs, "return_request_id"), rs.getString("direction"), rs.getString("provider"),
                rs.getString("provider_order_id"), rs.getString("provider_shipment_id"),
                rs.getString("courier_company_id"), rs.getString("courier_name"), rs.getString("awb_code"),
                rs.getString("status"), rs.getString("pickup_status"), rs.getString("tracking_url"),
                rs.getLong("shipping_charge"), nullableDate(rs, "estimated_pickup_date"),
                nullableDate(rs, "estimated_delivery_date"), rs.getString("last_location"),
                rs.getString("failure_description"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private ShipmentResponse response(ShipmentRow row) {
        List<ShipmentEventResponse> events = jdbc.sql("""
                SELECT provider_status,normalized_status,message,location,event_at FROM shipment_events
                WHERE shipment_id=:id ORDER BY event_at,id
                """).param("id", row.id()).query((rs, n) -> new ShipmentEventResponse(rs.getString("provider_status"),
                        rs.getString("normalized_status"), rs.getString("message"), rs.getString("location"),
                        rs.getTimestamp("event_at").toInstant())).list();
        return new ShipmentResponse(row.id(), row.orderNumber(), row.returnRequestId(), row.direction(), row.provider(),
                row.providerOrderId(), row.providerShipmentId(), row.courierName(), row.awbCode(), row.status(),
                row.pickupStatus(), row.trackingUrl(), row.shippingCharge(), row.estimatedPickupDate(),
                row.estimatedDeliveryDate(), row.lastLocation(), row.failureDescription(), events,
                row.createdAt(), row.updatedAt());
    }

    private ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SHIPPING_PROVIDER_UNAVAILABLE", "Shipping is not configured");
    }
    private JsonNode tree(String value) {
        try { return objectMapper.readTree(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Invalid persisted JSON", exception); }
    }
    private String json(Object value) {
        if (value == null) return null;
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Could not persist shipping response", exception); }
    }
    private static String first(JsonNode node, String... fields) {
        for (String field : fields) if (node.hasNonNull(field) && !node.path(field).asText().isBlank()) return node.path(field).asText();
        return null;
    }
    private static boolean constantTime(String expected, String actual) {
        if (actual == null) return false;
        return java.security.MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                actual.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try { return LocalDate.parse(value.substring(0, Math.min(10, value.length()))); }
        catch (RuntimeException ignored) { return null; }
    }
    private static String eventKey(String value) {
        try {
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
    private static Long nullableLong(ResultSet rs, String field) throws SQLException {
        long value = rs.getLong(field); return rs.wasNull() ? null : value;
    }
    private static LocalDate nullableDate(ResultSet rs, String field) throws SQLException {
        var date = rs.getDate(field); return date == null ? null : date.toLocalDate();
    }

    private record PackageRow(int weightGrams, BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm) {}
    private record QuoteRow(String courierCompanyId, String courierName, long providerCharge,
                            LocalDate estimatedDeliveryDate, boolean serviceable) {}
    private record ForwardRow(long id, String orderNumber, String status, JsonNode address, long subtotal,
                              LocalDate orderDate, Long shippingQuoteId, String email, String mobile,
                              List<ShippingGateway.LineItem> items, PackageRow parcel) {}
    private record ReverseRow(long id, long orderId, String orderNumber, String status, JsonNode pickup,
                              long subtotal, LocalDate requestedAt, String email, String mobile,
                              List<ShippingGateway.LineItem> items) {}
    private record ShipmentRow(long id, long orderId, String orderNumber, Long returnRequestId,
                               String direction, String provider, String providerOrderId,
                               String providerShipmentId, String courierCompanyId, String courierName,
                               String awbCode, String status, String pickupStatus, String trackingUrl,
                               long shippingCharge, LocalDate estimatedPickupDate,
                               LocalDate estimatedDeliveryDate, String lastLocation,
                               String failureDescription, Instant createdAt, Instant updatedAt) {}
}
