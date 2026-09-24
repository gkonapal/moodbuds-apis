package com.moodbuds.returns;

import static com.moodbuds.returns.api.ReturnDtos.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodbuds.common.ApiException;
import com.moodbuds.common.PageResponse;
import com.moodbuds.customer.CustomerProfileService;
import com.moodbuds.shipping.ShippingService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerReturnService {
    private final JdbcClient jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final ObjectMapper objectMapper;
    private final CustomerProfileService customers;
    private final ShippingService shipping;

    public CustomerReturnService(JdbcClient jdbc, NamedParameterJdbcTemplate namedJdbc,
                                 ObjectMapper objectMapper, CustomerProfileService customers,
                                 ShippingService shipping) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
        this.objectMapper = objectMapper;
        this.customers = customers;
        this.shipping = shipping;
    }

    public ReturnEligibilityResponse eligibility(long customerId, String orderNumber) {
        customers.requireActive(customerId);
        OrderRow order = ownedOrder(customerId, orderNumber, false);
        var items = eligibilityItems(order);
        boolean canReturn = order.deliveredAt() != null && items.stream().anyMatch(ReturnItemEligibility::eligible);
        String reason = order.deliveredAt() == null ? "ORDER_NOT_DELIVERED"
                : canReturn ? null : "NO_RETURNABLE_ITEMS";
        return new ReturnEligibilityResponse(order.orderNumber(), order.status(), order.deliveredAt(),
                canReturn, reason, items);
    }

    @Transactional
    public ReturnDetailResponse create(long customerId, String orderNumber, String idempotencyKey,
                                       CreateReturnRequest request) {
        customers.requireActive(customerId);
        String key = idempotencyKey.trim();
        String fingerprint = fingerprint(request);
        jdbc.sql("SELECT id FROM users WHERE id=:id FOR UPDATE").param("id", customerId).query(Long.class).single();
        ExistingReturn existing = jdbc.sql("""
                SELECT id,idempotency_fingerprint FROM return_requests
                WHERE user_id=:userId AND idempotency_key=:key
                """).param("userId", customerId).param("key", key)
                .query((rs, rowNum) -> new ExistingReturn(rs.getLong("id"),
                        rs.getString("idempotency_fingerprint"))).optional().orElse(null);
        if (existing != null) {
            if (!fingerprint.equals(existing.fingerprint())) {
                throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                        "This Idempotency-Key was already used with different return details");
            }
            return get(customerId, existing.id());
        }

        OrderRow order = ownedOrder(customerId, orderNumber, true);
        if (order.deliveredAt() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_NOT_DELIVERED",
                    "Returns can be requested only after the order is delivered");
        }
        if (request.reason() == ReturnReason.OTHER
                && (request.reasonDescription() == null || request.reasonDescription().isBlank())) {
            throw ApiException.badRequest("RETURN_DESCRIPTION_REQUIRED",
                    "A description is required when the return reason is OTHER");
        }
        var pickupAddress = customers.address(customerId, request.pickupAddressId());
        var uniqueItems = new HashSet<Long>();
        for (ReturnItemRequest requested : request.items()) {
            if (!uniqueItems.add(requested.orderItemId())) {
                throw ApiException.badRequest("DUPLICATE_RETURN_ITEM",
                        "Each order item can appear only once in a return request");
            }
            requireReturnableItem(order, requested);
        }

        var keys = new GeneratedKeyHolder();
        namedJdbc.update("""
                INSERT INTO return_requests(order_id,user_id,idempotency_key,idempotency_fingerprint,status,
                    reason,reason_description,pickup_address_id,pickup_address_snapshot_full,images,
                    requested_at,updated_at)
                VALUES(:orderId,:userId,:key,:fingerprint,'REQUESTED',:reason,:description,:addressId,
                    :addressSnapshot,:images,UTC_TIMESTAMP(),UTC_TIMESTAMP())
                """, new MapSqlParameterSource().addValue("orderId", order.id()).addValue("userId", customerId)
                .addValue("key", key).addValue("fingerprint", fingerprint)
                .addValue("reason", request.reason().name()).addValue("description", clean(request.reasonDescription()))
                .addValue("addressId", request.pickupAddressId()).addValue("addressSnapshot", json(pickupAddress))
                .addValue("images", json(request.images() == null ? List.of() : request.images())),
                keys, new String[]{"id"});
        long returnId = keys.getKey().longValue();
        for (ReturnItemRequest requested : request.items()) {
            jdbc.sql("""
                    INSERT INTO return_items(return_request_id,order_item_id,quantity_to_return,reason)
                    VALUES(:returnId,:orderItemId,:quantity,:reason)
                    """).param("returnId", returnId).param("orderItemId", requested.orderItemId())
                    .param("quantity", requested.quantity()).param("reason", requested.reason().name()).update();
        }
        jdbc.sql("""
                INSERT INTO return_status_history(return_request_id,from_status,to_status,notes,reason,
                    actor_type,actor_id,created_at)
                VALUES(:returnId,NULL,'REQUESTED','Customer submitted a return request',NULL,
                    'CUSTOMER',:customerId,UTC_TIMESTAMP())
                """).param("returnId", returnId).param("customerId", customerId).update();
        if ("DELIVERED".equals(order.status())) {
            jdbc.sql("UPDATE orders SET status='RETURN_INITIATED',updated_at=UTC_TIMESTAMP() WHERE id=:id")
                    .param("id", order.id()).update();
            jdbc.sql("""
                    INSERT INTO order_status_history(order_id,from_status,to_status,notes,changed_by_id,created_at)
                    VALUES(:orderId,'DELIVERED','RETURN_INITIATED','Customer submitted a return request',
                        :customerId,UTC_TIMESTAMP())
                    """).param("orderId", order.id()).param("customerId", customerId).update();
        }
        return get(customerId, returnId);
    }

    public PageResponse<ReturnSummaryResponse> list(long customerId, ReturnStatus status, String orderNumber,
                                                    int page, int size) {
        customers.requireActive(customerId);
        int boundedSize = Math.min(Math.max(size, 1), 100);
        int boundedPage = Math.max(page, 0);
        String statusClause = status == null ? "" : " AND rr.status=:status";
        String orderClause = orderNumber == null || orderNumber.isBlank() ? "" : " AND o.order_number=:orderNumber";
        var query = jdbc.sql("""
                SELECT rr.id,o.order_number,rr.status,rr.reason,rr.requested_at,rr.updated_at,
                       COUNT(ri.id) item_count,COALESCE(SUM(ri.quantity_to_return),0) total_quantity
                FROM return_requests rr JOIN orders o ON o.id=rr.order_id
                LEFT JOIN return_items ri ON ri.return_request_id=rr.id
                WHERE rr.user_id=:userId
                """ + statusClause + orderClause + " GROUP BY rr.id ORDER BY rr.id DESC LIMIT :limit OFFSET :offset")
                .param("userId", customerId).param("limit", boundedSize).param("offset", boundedPage * boundedSize);
        var count = jdbc.sql("SELECT COUNT(*) FROM return_requests rr JOIN orders o ON o.id=rr.order_id "
                        + "WHERE rr.user_id=:userId" + statusClause + orderClause)
                .param("userId", customerId);
        if (status != null) {
            query = query.param("status", status.name());
            count = count.param("status", status.name());
        }
        if (!orderClause.isEmpty()) {
            query = query.param("orderNumber", orderNumber.trim());
            count = count.param("orderNumber", orderNumber.trim());
        }
        var content = query.query((rs, rowNum) -> new ReturnSummaryResponse(rs.getLong("id"),
                rs.getString("order_number"), ReturnStatus.valueOf(rs.getString("status")),
                ReturnReason.valueOf(rs.getString("reason")), rs.getInt("item_count"),
                rs.getInt("total_quantity"), instant(rs, "requested_at"), instant(rs, "updated_at"))).list();
        return PageResponse.of(content, boundedPage, boundedSize, count.query(Long.class).single());
    }

    public ReturnDetailResponse get(long customerId, long returnId) {
        customers.requireActive(customerId);
        ReturnRow row = jdbc.sql("""
                SELECT rr.*,o.order_number FROM return_requests rr JOIN orders o ON o.id=rr.order_id
                WHERE rr.id=:id AND rr.user_id=:userId
                """).param("id", returnId).param("userId", customerId)
                .query((rs, rowNum) -> returnRow(rs)).optional()
                .orElseThrow(() -> ApiException.notFound("Return request"));
        var items = jdbc.sql("""
                SELECT ri.id,ri.order_item_id,ri.quantity_to_return,ri.reason,ri.condition_on_receipt,
                       oi.product_snapshot,oi.size
                FROM return_items ri JOIN order_items oi ON oi.id=ri.order_item_id
                WHERE ri.return_request_id=:id ORDER BY ri.id
                """).param("id", returnId).query((rs, rowNum) -> new ReturnItemResponse(rs.getLong("id"),
                        rs.getLong("order_item_id"), tree(rs.getString("product_snapshot")), rs.getString("size"),
                        rs.getInt("quantity_to_return"), ReturnItemReason.valueOf(rs.getString("reason")),
                        rs.getString("condition_on_receipt"))).list();
        var history = jdbc.sql("""
                SELECT id,from_status,to_status,notes,reason,actor_type,created_at
                FROM return_status_history WHERE return_request_id=:id ORDER BY created_at,id
                """).param("id", returnId).query((rs, rowNum) -> new ReturnHistoryResponse(
                        rs.getLong("id"), rs.getString("from_status"), rs.getString("to_status"),
                        rs.getString("notes"), rs.getString("reason"), rs.getString("actor_type"),
                        instant(rs, "created_at"))).list();
        var refund = jdbc.sql("""
                SELECT id,amount,currency,status,provider_reference,failure_description,
                       initiated_at,completed_at,updated_at
                FROM refunds WHERE return_request_id=:id ORDER BY id DESC LIMIT 1
                """).param("id", returnId).query((rs, rowNum) -> new ReturnRefundResponse(
                        rs.getLong("id"), rs.getLong("amount"), rs.getString("currency"),
                        rs.getString("status"), rs.getString("provider_reference"),
                        rs.getString("failure_description"), instant(rs, "initiated_at"),
                        nullableInstant(rs, "completed_at"), instant(rs, "updated_at")))
                .optional().orElse(null);
        return new ReturnDetailResponse(row.id(), row.orderNumber(), ReturnStatus.valueOf(row.status()),
                ReturnReason.valueOf(row.reason()), row.reasonDescription(), row.pickupAddressId(),
                tree(row.pickupAddress()), tree(row.images()), row.adminNotes(), row.rejectionReason(),
                items, history, refund, shipping.forReturn(customerId, returnId),
                row.requestedAt(), row.updatedAt());
    }

    private List<ReturnItemEligibility> eligibilityItems(OrderRow order) {
        return jdbc.sql("""
                SELECT oi.id,oi.product_snapshot,oi.size,oi.quantity,oi.return_window_days_snapshot,
                       COALESCE((SELECT SUM(ri.quantity_to_return) FROM return_items ri
                           JOIN return_requests rr ON rr.id=ri.return_request_id
                           WHERE ri.order_item_id=oi.id AND rr.status<>'REJECTED'),0) requested_quantity
                FROM order_items oi WHERE oi.order_id=:orderId ORDER BY oi.id
                """).param("orderId", order.id()).query((rs, rowNum) -> {
                    int purchased = rs.getInt("quantity");
                    int requested = rs.getInt("requested_quantity");
                    int available = Math.max(purchased - requested, 0);
                    int days = rs.getInt("return_window_days_snapshot");
                    Instant until = order.deliveredAt() == null ? null
                            : order.deliveredAt().plus(days, ChronoUnit.DAYS);
                    boolean eligible = order.deliveredAt() != null && days > 0 && available > 0
                            && !Instant.now().isAfter(until);
                    String reason = days <= 0 ? "ITEM_NOT_RETURNABLE" : available <= 0 ? "QUANTITY_EXHAUSTED"
                            : order.deliveredAt() == null ? "ORDER_NOT_DELIVERED"
                            : Instant.now().isAfter(until) ? "RETURN_WINDOW_EXPIRED" : null;
                    return new ReturnItemEligibility(rs.getLong("id"), tree(rs.getString("product_snapshot")),
                            rs.getString("size"), purchased, requested, available, days, until, eligible, reason);
                }).list();
    }

    private void requireReturnableItem(OrderRow order, ReturnItemRequest request) {
        ItemRow item = jdbc.sql("""
                SELECT oi.id,oi.quantity,oi.return_window_days_snapshot,
                       COALESCE((SELECT SUM(ri.quantity_to_return) FROM return_items ri
                           JOIN return_requests rr ON rr.id=ri.return_request_id
                           WHERE ri.order_item_id=oi.id AND rr.status<>'REJECTED'),0) requested_quantity
                FROM order_items oi WHERE oi.id=:itemId AND oi.order_id=:orderId FOR UPDATE
                """).param("itemId", request.orderItemId()).param("orderId", order.id())
                .query((rs, rowNum) -> new ItemRow(rs.getLong("id"), rs.getInt("quantity"),
                        rs.getInt("return_window_days_snapshot"), rs.getInt("requested_quantity")))
                .optional().orElseThrow(() -> ApiException.notFound("Order item"));
        if (item.returnWindowDays() <= 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "ITEM_NOT_RETURNABLE",
                    "This item is not returnable");
        }
        if (Instant.now().isAfter(order.deliveredAt().plus(item.returnWindowDays(), ChronoUnit.DAYS))) {
            throw new ApiException(HttpStatus.CONFLICT, "RETURN_WINDOW_EXPIRED",
                    "The return window for this item has expired");
        }
        int available = item.purchasedQuantity() - item.requestedQuantity();
        if (request.quantity() > available) {
            throw new ApiException(HttpStatus.CONFLICT, "RETURN_QUANTITY_UNAVAILABLE",
                    "The requested return quantity exceeds the remaining returnable quantity");
        }
    }

    private OrderRow ownedOrder(long customerId, String orderNumber, boolean lock) {
        return jdbc.sql("""
                SELECT o.id,o.order_number,o.status,
                       (SELECT MAX(h.created_at) FROM order_status_history h
                        WHERE h.order_id=o.id AND h.to_status='DELIVERED') delivered_at
                FROM orders o WHERE o.user_id=:userId AND o.order_number=:orderNumber
                """ + (lock ? " FOR UPDATE" : "")).param("userId", customerId).param("orderNumber", orderNumber)
                .query((rs, rowNum) -> new OrderRow(rs.getLong("id"), rs.getString("order_number"),
                        rs.getString("status"), nullableInstant(rs, "delivered_at"))).optional()
                .orElseThrow(() -> ApiException.notFound("Order"));
    }

    private ReturnRow returnRow(ResultSet rs) throws SQLException {
        return new ReturnRow(rs.getLong("id"), rs.getString("order_number"), rs.getString("status"),
                rs.getString("reason"), rs.getString("reason_description"), rs.getLong("pickup_address_id"),
                rs.getString("pickup_address_snapshot_full"), rs.getString("images"),
                rs.getString("admin_notes"), rs.getString("rejection_reason"),
                instant(rs, "requested_at"), instant(rs, "updated_at"));
    }

    private String fingerprint(CreateReturnRequest request) {
        var items = request.items().stream().sorted(Comparator.comparingLong(ReturnItemRequest::orderItemId)).toList();
        String value = request.reason() + "|" + clean(request.reasonDescription()) + "|" + request.pickupAddressId()
                + "|" + (request.images() == null ? List.of() : request.images()) + "|" + items;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private JsonNode tree(String value) {
        if (value == null) return null;
        try { return objectMapper.readTree(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Invalid persisted JSON", exception); }
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Could not persist JSON", exception); }
    }

    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static Instant instant(ResultSet rs, String field) throws SQLException { return rs.getTimestamp(field).toInstant(); }
    private static Instant nullableInstant(ResultSet rs, String field) throws SQLException {
        var timestamp = rs.getTimestamp(field); return timestamp == null ? null : timestamp.toInstant();
    }

    private record ExistingReturn(long id, String fingerprint) {}
    private record OrderRow(long id, String orderNumber, String status, Instant deliveredAt) {}
    private record ItemRow(long id, int purchasedQuantity, int returnWindowDays, int requestedQuantity) {}
    private record ReturnRow(long id, String orderNumber, String status, String reason,
                             String reasonDescription, long pickupAddressId, String pickupAddress,
                             String images, String adminNotes, String rejectionReason,
                             Instant requestedAt, Instant updatedAt) {}
}
