package com.moodbuds.refund;

import static com.moodbuds.refund.api.RefundDtos.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodbuds.common.ApiException;
import com.moodbuds.common.PageResponse;
import com.moodbuds.coupon.CouponCalculations;
import com.moodbuds.payment.RazorpayGateway.ProviderRefund;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefundStore {
    private final JdbcClient jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final ObjectMapper objectMapper;

    public RefundStore(JdbcClient jdbc, NamedParameterJdbcTemplate namedJdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public long prepare(long returnId, String idempotencyKey, RefundSpeed speed) {
        ReturnRefundRow returned = returnForUpdate(returnId);
        ExistingRefund existing = jdbc.sql("""
                SELECT id,idempotency_key,status FROM refunds
                WHERE return_request_id=:id ORDER BY id DESC LIMIT 1
                """)
                .param("id", returnId).query((rs, rowNum) -> new ExistingRefund(rs.getLong("id"),
                        rs.getString("idempotency_key"), rs.getString("status"))).optional().orElse(null);
        if (existing != null) {
            if (idempotencyKey.equals(existing.idempotencyKey())) return existing.id();
            if (!"FAILED".equals(existing.status())) {
                throw new ApiException(HttpStatus.CONFLICT, "REFUND_ALREADY_EXISTS",
                        "A refund already exists for this return request");
            }
        }
        if (!"QUALITY_CHECK_PASSED".equals(returned.returnStatus())
                && !(existing != null && "FAILED".equals(existing.status())
                && "REFUND_INITIATED".equals(returned.returnStatus()))) {
            throw new ApiException(HttpStatus.CONFLICT, "RETURN_NOT_READY_FOR_REFUND",
                    "Refunds can be initiated only after return quality checking passes");
        }
        if (returned.gatewayPaymentId() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "CAPTURED_PAYMENT_NOT_FOUND",
                    "A refundable captured payment was not found for this order");
        }

        var allLines = jdbc.sql("""
                SELECT id,quantity,line_total,gst_amount FROM order_items WHERE order_id=:orderId ORDER BY id
                """).param("orderId", returned.orderId())
                .query((rs, rowNum) -> new OrderLine(rs.getLong("id"), rs.getInt("quantity"),
                        rs.getLong("line_total"), rs.getLong("gst_amount"))).list();
        List<Long> couponAllocations = CouponCalculations.allocateDiscount(
                allLines.stream().map(OrderLine::lineTotal).toList(), returned.couponDiscount());
        Map<Long, Long> refundableLines = new HashMap<>();
        for (int index = 0; index < allLines.size(); index++) {
            OrderLine line = allLines.get(index);
            refundableLines.put(line.id(), line.lineTotal() - couponAllocations.get(index));
        }

        var items = jdbc.sql("""
                SELECT ri.id,ri.order_item_id,ri.quantity_to_return,ri.condition_on_receipt,
                       oi.product_id,oi.size,oi.quantity purchased_quantity
                FROM return_items ri JOIN order_items oi ON oi.id=ri.order_item_id
                WHERE ri.return_request_id=:returnId ORDER BY ri.id FOR UPDATE
                """).param("returnId", returnId).query((rs, rowNum) -> new RefundItem(
                        rs.getLong("id"), rs.getLong("order_item_id"), rs.getInt("quantity_to_return"),
                        rs.getString("condition_on_receipt"), rs.getLong("product_id"),
                        rs.getString("size"), rs.getInt("purchased_quantity"))).list();
        if (items.isEmpty() || items.stream().anyMatch(item -> item.condition() == null)) {
            throw new ApiException(HttpStatus.CONFLICT, "RETURN_INSPECTION_INCOMPLETE",
                    "Every returned item must have a warehouse inspection condition before refund initiation");
        }

        var allocations = new HashMap<Long, Long>();
        long refundAmount = 0;
        for (RefundItem item : items) {
            Allocation allocated = previousAllocation(item.orderItemId());
            long amount = RefundCalculations.itemAmount(refundableLines.get(item.orderItemId()),
                    item.purchasedQuantity(), allocated.quantity(), allocated.amount(), item.quantity());
            allocations.put(item.returnItemId(), amount);
            refundAmount = Math.addExact(refundAmount, amount);
        }
        long reservedRefunds = jdbc.sql("""
                SELECT COALESCE(SUM(amount),0) FROM refunds
                WHERE payment_id=:paymentId AND status<>'FAILED'
                """)
                .param("paymentId", returned.paymentId()).query(Long.class).single();
        if (refundAmount <= 0 || refundAmount > returned.paymentAmount() - reservedRefunds) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_REFUND_AMOUNT",
                    "The calculated refund exceeds the payment's remaining refundable amount");
        }

        var keys = new GeneratedKeyHolder();
        namedJdbc.update("""
                INSERT INTO refunds(order_id,payment_id,return_request_id,idempotency_key,amount,currency,
                    speed_requested,reason,status,initiated_at,updated_at)
                VALUES(:orderId,:paymentId,:returnId,:key,:amount,:currency,:speed,:reason,'PENDING',
                    UTC_TIMESTAMP(),UTC_TIMESTAMP())
                """, new MapSqlParameterSource().addValue("orderId", returned.orderId())
                .addValue("paymentId", returned.paymentId()).addValue("returnId", returnId)
                .addValue("key", idempotencyKey).addValue("amount", refundAmount)
                .addValue("currency", returned.currency()).addValue("speed", speed.name())
                .addValue("reason", "Return " + returnId + ": " + returned.reason()), keys, new String[]{"id"});
        long refundId = keys.getKey().longValue();
        allocations.forEach((returnItemId, amount) -> jdbc.sql("""
                INSERT INTO refund_items(refund_id,return_item_id,amount) VALUES(:refundId,:returnItemId,:amount)
                """).param("refundId", refundId).param("returnItemId", returnItemId).param("amount", amount).update());

        restockGoodItems(returned, items);
        jdbc.sql("""
                UPDATE return_requests SET status='REFUND_INITIATED',inventory_restocked_at=COALESCE(
                    inventory_restocked_at,UTC_TIMESTAMP()),updated_at=UTC_TIMESTAMP() WHERE id=:id
                """).param("id", returnId).update();
        transitionOrder(returned.orderId(), returned.orderStatus(), "REFUND_INITIATED",
                "Return approved; refund initiated");
        return refundId;
    }

    @Transactional
    public Operation recordAttempt(long refundId) {
        Operation operation = operation(refundId, true);
        if (List.of("PROCESSED", "FAILED").contains(operation.status())) return operation;
        jdbc.sql("""
                UPDATE refunds SET provider_attempt_count=provider_attempt_count+1,
                    next_retry_at=DATE_ADD(UTC_TIMESTAMP(),INTERVAL 5 MINUTE),
                    updated_at=UTC_TIMESTAMP() WHERE id=:id
                """).param("id", refundId).update();
        return operation(refundId, false);
    }

    @Transactional
    public RefundResponse apply(long refundId, ProviderRefund provider) {
        Operation operation = operation(refundId, true);
        if (List.of("PROCESSED", "FAILED").contains(operation.status())) {
            return response(refundId, null);
        }
        if (!operation.gatewayPaymentId().equals(provider.paymentId()) || operation.amount() != provider.amount()
                || !operation.currency().equals(provider.currency())) {
            throw new ApiException(HttpStatus.CONFLICT, "REFUND_DETAILS_MISMATCH",
                    "The Razorpay refund does not match the local refund request");
        }
        String status = switch (provider.status()) {
            case "processed" -> "PROCESSED";
            case "failed" -> "FAILED";
            case "pending" -> "INITIATED";
            default -> throw new ApiException(HttpStatus.BAD_GATEWAY, "RAZORPAY_INVALID_RESPONSE",
                    "Razorpay returned an unsupported refund status");
        };
        jdbc.sql("""
                UPDATE refunds SET gateway_refund_id=:gatewayId,status=:status,speed_processed=:speedProcessed,
                    provider_reference=:reference,gateway_response=:response,failure_code=NULL,
                    failure_description=NULL,next_retry_at=CASE WHEN :status='INITIATED'
                        THEN DATE_ADD(UTC_TIMESTAMP(),INTERVAL 5 MINUTE) ELSE NULL END,
                    last_reconciled_at=UTC_TIMESTAMP(),completed_at=CASE WHEN :status IN ('PROCESSED','FAILED')
                        THEN UTC_TIMESTAMP() ELSE NULL END,updated_at=UTC_TIMESTAMP() WHERE id=:id
                """).param("gatewayId", provider.id()).param("status", status)
                .param("speedProcessed", provider.speedProcessed()).param("reference", provider.providerReference())
                .param("response", json(provider.raw())).param("id", refundId).update();
        if ("PROCESSED".equals(status)) complete(operation);
        return response(refundId, null);
    }

    @Transactional
    public void recordProviderFailure(long refundId, ApiException exception) {
        jdbc.sql("""
                UPDATE refunds SET failure_code=:code,failure_description=:description,
                    next_retry_at=COALESCE(next_retry_at,DATE_ADD(UTC_TIMESTAMP(),INTERVAL 5 MINUTE)),
                    updated_at=UTC_TIMESTAMP() WHERE id=:id AND status IN ('PENDING','INITIATED')
                """).param("code", exception.code()).param("description", exception.getMessage())
                .param("id", refundId).update();
    }

    @Transactional
    public Map<String, Object> inspect(long returnId, long returnItemId, ItemCondition condition) {
        var request = jdbc.sql("SELECT status FROM return_requests WHERE id=:id FOR UPDATE")
                .param("id", returnId).query(String.class).optional()
                .orElseThrow(() -> ApiException.notFound("Return request"));
        if (!"ITEM_RECEIVED".equals(request)) {
            throw new ApiException(HttpStatus.CONFLICT, "RETURN_NOT_READY_FOR_INSPECTION",
                    "Items can be inspected only after the return is received");
        }
        int updated = jdbc.sql("""
                UPDATE return_items SET condition_on_receipt=:condition
                WHERE id=:itemId AND return_request_id=:returnId
                """).param("condition", condition.name()).param("itemId", returnItemId)
                .param("returnId", returnId).update();
        if (updated == 0) throw ApiException.notFound("Return item");
        return jdbc.sql("SELECT * FROM return_items WHERE id=:id").param("id", returnItemId).query().singleRow();
    }

    public Operation operation(long refundId) { return operation(refundId, false); }

    public List<Long> dueRefundIds(int limit) {
        return jdbc.sql("""
                SELECT id FROM refunds WHERE status IN ('PENDING','INITIATED')
                  AND (next_retry_at IS NULL OR next_retry_at<=UTC_TIMESTAMP())
                ORDER BY initiated_at,id LIMIT :limit
                """).param("limit", Math.min(Math.max(limit, 1), 500)).query(Long.class).list();
    }

    public RefundResponse customerRefund(long customerId, long refundId) {
        return response(refundId, customerId);
    }

    public PageResponse<RefundResponse> customerRefunds(long customerId, int page, int size) {
        return list(customerId, page, size);
    }

    public RefundResponse adminRefund(long refundId) { return response(refundId, null); }
    public PageResponse<RefundResponse> adminRefunds(int page, int size) { return list(null, page, size); }

    private PageResponse<RefundResponse> list(Long customerId, int page, int size) {
        int boundedSize = Math.min(Math.max(size, 1), 100), boundedPage = Math.max(page, 0);
        String where = customerId == null ? "" : " WHERE o.user_id=:userId";
        var query = jdbc.sql(refundSelect() + where + " ORDER BY r.id DESC LIMIT :limit OFFSET :offset")
                .param("limit", boundedSize).param("offset", boundedPage * boundedSize);
        var count = jdbc.sql("SELECT COUNT(*) FROM refunds r JOIN orders o ON o.id=r.order_id" + where);
        if (customerId != null) { query = query.param("userId", customerId); count = count.param("userId", customerId); }
        return PageResponse.of(query.query((rs, rowNum) -> response(rs)).list(), boundedPage, boundedSize,
                count.query(Long.class).single());
    }

    private RefundResponse response(long refundId, Long customerId) {
        String owner = customerId == null ? "" : " AND o.user_id=:userId";
        var query = jdbc.sql(refundSelect() + " WHERE r.id=:id" + owner).param("id", refundId);
        if (customerId != null) query = query.param("userId", customerId);
        return query.query((rs, rowNum) -> response(rs)).optional()
                .orElseThrow(() -> ApiException.notFound("Refund"));
    }

    private String refundSelect() {
        return """
                SELECT r.*,o.order_number FROM refunds r JOIN orders o ON o.id=r.order_id
                """;
    }

    private RefundResponse response(ResultSet rs) throws SQLException {
        return new RefundResponse(rs.getLong("id"), rs.getString("order_number"),
                rs.getLong("return_request_id"), rs.getString("gateway_refund_id"), rs.getLong("amount"),
                rs.getString("currency"), RefundStatus.valueOf(rs.getString("status")),
                RefundSpeed.valueOf(rs.getString("speed_requested")), rs.getString("speed_processed"),
                rs.getString("provider_reference"), rs.getString("failure_code"),
                rs.getString("failure_description"), rs.getInt("provider_attempt_count"),
                nullableInstant(rs, "next_retry_at"), instant(rs, "initiated_at"),
                nullableInstant(rs, "completed_at"), instant(rs, "updated_at"));
    }

    private Operation operation(long refundId, boolean lock) {
        return jdbc.sql("""
                SELECT r.id,r.status,r.amount,r.currency,r.idempotency_key,r.gateway_refund_id,r.reason,
                       r.speed_requested,p.gateway_payment_id,o.order_number
                FROM refunds r JOIN payments p ON p.id=r.payment_id JOIN orders o ON o.id=r.order_id
                WHERE r.id=:id
                """ + (lock ? " FOR UPDATE" : "")).param("id", refundId)
                .query((rs, rowNum) -> new Operation(rs.getLong("id"), rs.getString("status"),
                        rs.getLong("amount"), rs.getString("currency"), rs.getString("idempotency_key"),
                        rs.getString("gateway_refund_id"), rs.getString("gateway_payment_id"),
                        rs.getString("order_number"), rs.getString("reason"),
                        RefundSpeed.valueOf(rs.getString("speed_requested")))).optional()
                .orElseThrow(() -> ApiException.notFound("Refund"));
    }

    private ReturnRefundRow returnForUpdate(long returnId) {
        return jdbc.sql("""
                SELECT rr.id return_id,rr.order_id,rr.status return_status,rr.reason,rr.inventory_restocked_at,
                       o.order_number,o.status order_status,o.coupon_discount,
                       p.id payment_id,p.gateway_payment_id,p.amount payment_amount,p.currency
                FROM return_requests rr JOIN orders o ON o.id=rr.order_id
                LEFT JOIN payments p ON p.id=(SELECT MAX(p2.id) FROM payments p2 WHERE p2.order_id=o.id
                    AND p2.status IN ('SUCCESS','PARTIALLY_REFUNDED','REFUNDED'))
                WHERE rr.id=:id FOR UPDATE
                """).param("id", returnId).query((rs, rowNum) -> new ReturnRefundRow(rs.getLong("return_id"),
                        rs.getLong("order_id"), rs.getString("return_status"), rs.getString("reason"),
                        nullableInstant(rs, "inventory_restocked_at"), rs.getString("order_number"),
                        rs.getString("order_status"), rs.getLong("coupon_discount"),
                        nullableLong(rs, "payment_id"), rs.getString("gateway_payment_id"),
                        rs.getLong("payment_amount"), rs.getString("currency"))).optional()
                .orElseThrow(() -> ApiException.notFound("Return request"));
    }

    private Allocation previousAllocation(long orderItemId) {
        return jdbc.sql("""
                SELECT COALESCE(SUM(fi.amount),0) amount,COALESCE(SUM(ri.quantity_to_return),0) quantity
                FROM refund_items fi JOIN return_items ri ON ri.id=fi.return_item_id
                JOIN refunds r ON r.id=fi.refund_id
                WHERE ri.order_item_id=:orderItemId AND r.status<>'FAILED'
                """).param("orderItemId", orderItemId)
                .query((rs, rowNum) -> new Allocation(rs.getLong("amount"), rs.getInt("quantity"))).single();
    }

    private void restockGoodItems(ReturnRefundRow returned, List<RefundItem> items) {
        if (returned.inventoryRestockedAt() != null) return;
        for (RefundItem item : items) {
            if (!"GOOD".equals(item.condition())) continue;
            int before = jdbc.sql("""
                    SELECT stock_quantity FROM product_sizes WHERE product_id=:productId AND size=:size FOR UPDATE
                    """).param("productId", item.productId()).param("size", item.size()).query(Integer.class).single();
            int after = Math.addExact(before, item.quantity());
            jdbc.sql("""
                    UPDATE product_sizes SET stock_quantity=:after,is_available=1,updated_at=UTC_TIMESTAMP()
                    WHERE product_id=:productId AND size=:size
                    """).param("after", after).param("productId", item.productId()).param("size", item.size()).update();
            jdbc.sql("""
                    INSERT INTO inventory_logs(product_id,size,change_type,quantity_change,quantity_before,
                        quantity_after,reference_type,created_by,created_at)
                    VALUES(:productId,:size,'RETURN',:quantity,:before,:after,:reference,NULL,UTC_TIMESTAMP())
                    """).param("productId", item.productId()).param("size", item.size())
                    .param("quantity", item.quantity()).param("before", before).param("after", after)
                    .param("reference", "return:" + returned.returnId()).update();
        }
    }

    private void complete(Operation operation) {
        var totals = jdbc.sql("""
                SELECT p.order_id,p.amount,COALESCE(SUM(CASE WHEN r.status='PROCESSED' THEN r.amount ELSE 0 END),0) refunded
                FROM payments p LEFT JOIN refunds r ON r.payment_id=p.id WHERE p.gateway_payment_id=:paymentId
                GROUP BY p.id
                """).param("paymentId", operation.gatewayPaymentId()).query().singleRow();
        long paymentAmount = ((Number) totals.get("amount")).longValue();
        long refunded = ((Number) totals.get("refunded")).longValue();
        String paymentStatus = refunded >= paymentAmount ? "REFUNDED" : "PARTIALLY_REFUNDED";
        jdbc.sql("UPDATE payments SET status=:status WHERE gateway_payment_id=:paymentId")
                .param("status", paymentStatus).param("paymentId", operation.gatewayPaymentId()).update();
        jdbc.sql("UPDATE return_requests rr JOIN refunds r ON r.return_request_id=rr.id SET rr.status='COMPLETED',rr.updated_at=UTC_TIMESTAMP() WHERE r.id=:id")
                .param("id", operation.id()).update();
        long orderId = ((Number) totals.get("order_id")).longValue();
        String orderStatus = refunded >= paymentAmount ? "REFUNDED" : "PARTIALLY_REFUNDED";
        String current = jdbc.sql("SELECT status FROM orders WHERE id=:id FOR UPDATE").param("id", orderId)
                .query(String.class).single();
        transitionOrder(orderId, current, orderStatus, "Razorpay refund processed");
    }

    private void transitionOrder(long orderId, String from, String to, String note) {
        if (to.equals(from)) return;
        jdbc.sql("UPDATE orders SET status=:status,updated_at=UTC_TIMESTAMP() WHERE id=:id")
                .param("status", to).param("id", orderId).update();
        jdbc.sql("""
                INSERT INTO order_status_history(order_id,from_status,to_status,notes,changed_by_id,created_at)
                VALUES(:orderId,:fromStatus,:toStatus,:note,NULL,UTC_TIMESTAMP())
                """).param("orderId", orderId).param("fromStatus", from).param("toStatus", to)
                .param("note", note).update();
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Could not persist provider response", exception); }
    }

    private static Instant instant(ResultSet rs, String field) throws SQLException { return rs.getTimestamp(field).toInstant(); }
    private static Instant nullableInstant(ResultSet rs, String field) throws SQLException {
        var value = rs.getTimestamp(field); return value == null ? null : value.toInstant();
    }
    private static Long nullableLong(ResultSet rs, String field) throws SQLException {
        long value = rs.getLong(field); return rs.wasNull() ? null : value;
    }

    public record Operation(long id, String status, long amount, String currency, String idempotencyKey,
                            String gatewayRefundId, String gatewayPaymentId, String orderNumber,
                            String reason, RefundSpeed speed) {}
    private record ExistingRefund(long id, String idempotencyKey, String status) {}
    private record ReturnRefundRow(long returnId, long orderId, String returnStatus, String reason,
                                   Instant inventoryRestockedAt, String orderNumber, String orderStatus,
                                   long couponDiscount, Long paymentId, String gatewayPaymentId,
                                   long paymentAmount, String currency) {}
    private record OrderLine(long id, int quantity, long lineTotal, long gstAmount) {}
    private record RefundItem(long returnItemId, long orderItemId, int quantity, String condition,
                              long productId, String size, int purchasedQuantity) {}
    private record Allocation(long amount, int quantity) {}
}
