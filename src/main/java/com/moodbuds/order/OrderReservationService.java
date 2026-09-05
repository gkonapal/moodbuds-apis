package com.moodbuds.order;

import static com.moodbuds.order.api.OrderDtos.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import com.moodbuds.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderReservationService {
    private static final List<String> DIRECTLY_CANCELLABLE = List.of("PENDING_PAYMENT", "PAYMENT_FAILED");
    private final JdbcClient jdbc;

    public OrderReservationService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public CancellationEligibilityResponse eligibility(long customerId, String orderNumber) {
        CancellationRow order = ownedOrder(customerId, orderNumber, false);
        return eligibility(order);
    }

    public CancellationCandidate candidate(long customerId, String orderNumber) {
        CancellationRow order = ownedOrder(customerId, orderNumber, false);
        return new CancellationCandidate(order.id(), order.userId(), order.orderNumber(), order.status(),
                hasOpenProviderAttempt(order.id()));
    }

    public CancellationCandidate candidate(String orderNumber) {
        CancellationRow order = orderByNumber(orderNumber, false);
        return new CancellationCandidate(order.id(), order.userId(), order.orderNumber(), order.status(),
                hasOpenProviderAttempt(order.id()));
    }

    public List<CancellationCandidate> expiredCandidates(int limit) {
        return jdbc.sql("""
                SELECT o.id,o.user_id,o.order_number,o.status,
                       EXISTS(SELECT 1 FROM payments p WHERE p.order_id=o.id
                              AND p.gateway_order_id IS NOT NULL AND p.status IN ('INITIATED','PENDING')) open_payment
                FROM orders o
                WHERE o.status IN ('PENDING_PAYMENT','PAYMENT_FAILED')
                  AND o.payment_expires_at<=UTC_TIMESTAMP() AND o.reservation_released_at IS NULL
                ORDER BY o.payment_expires_at,o.id LIMIT :limit
                """).param("limit", Math.min(Math.max(limit, 1), 500))
                .query((rs, rowNum) -> new CancellationCandidate(rs.getLong("id"), rs.getLong("user_id"),
                        rs.getString("order_number"), rs.getString("status"), rs.getBoolean("open_payment"))).list();
    }

    @Transactional
    public void cancelByCustomer(long customerId, String orderNumber, String reason) {
        CancellationRow order = ownedOrder(customerId, orderNumber, true);
        if ("CANCELLED".equals(order.status())) return;
        requireDirectCancellation(order);
        cancelAndRelease(order, reason.trim(), customerId);
    }

    @Transactional
    public void cancelByAdmin(String orderNumber, long adminId, String reason) {
        CancellationRow order = orderByNumber(orderNumber, true);
        if ("CANCELLED".equals(order.status())) return;
        requireDirectCancellation(order);
        String note = reason == null || reason.isBlank() ? "Cancelled by administrator" : reason.trim();
        cancelAndRelease(order, note, adminId);
    }

    @Transactional
    public void expire(long orderId) {
        CancellationRow order = orderById(orderId, true);
        if ("CANCELLED".equals(order.status()) || order.reservationReleasedAt() != null) return;
        if (!DIRECTLY_CANCELLABLE.contains(order.status()) || order.paymentExpiresAt().isAfter(Instant.now())) return;
        cancelAndRelease(order, "Payment window expired", null);
    }

    private void cancelAndRelease(CancellationRow order, String reason, Long changedBy) {
        releaseStock(order);
        releaseCoupon(order);
        jdbc.sql("""
                UPDATE payments SET status='EXPIRED',completed_at=UTC_TIMESTAMP()
                WHERE order_id=:orderId AND status IN ('INITIATED','PENDING')
                """).param("orderId", order.id()).update();
        jdbc.sql("""
                UPDATE orders SET status='CANCELLED',cancellation_reason=:reason,cancelled_at=UTC_TIMESTAMP(),
                    reservation_released_at=UTC_TIMESTAMP(),updated_at=UTC_TIMESTAMP() WHERE id=:id
                """).param("reason", reason).param("id", order.id()).update();
        jdbc.sql("""
                INSERT INTO order_status_history(order_id,from_status,to_status,notes,changed_by_id,created_at)
                VALUES(:orderId,:fromStatus,'CANCELLED',:notes,:changedBy,UTC_TIMESTAMP())
                """).param("orderId", order.id()).param("fromStatus", order.status())
                .param("notes", reason).param("changedBy", changedBy).update();
    }

    private void releaseStock(CancellationRow order) {
        var items = jdbc.sql("""
                SELECT product_id,size,SUM(quantity) quantity FROM order_items
                WHERE order_id=:orderId GROUP BY product_id,size ORDER BY product_id,size
                """).param("orderId", order.id()).query((rs, rowNum) -> new ReservedItem(
                        rs.getLong("product_id"), rs.getString("size"), rs.getInt("quantity"))).list();
        for (ReservedItem item : items) {
            Stock stock = jdbc.sql("""
                    SELECT stock_quantity,is_available FROM product_sizes
                    WHERE product_id=:productId AND size=:size FOR UPDATE
                    """).param("productId", item.productId()).param("size", item.size())
                    .query((rs, rowNum) -> new Stock(rs.getInt("stock_quantity"), rs.getBoolean("is_available")))
                    .optional().orElseThrow(() -> new IllegalStateException("Reserved product size no longer exists"));
            int after = Math.addExact(stock.quantity(), item.quantity());
            boolean available = stock.available() || stock.quantity() == 0;
            jdbc.sql("""
                    UPDATE product_sizes SET stock_quantity=:after,is_available=:available,updated_at=UTC_TIMESTAMP()
                    WHERE product_id=:productId AND size=:size
                    """).param("after", after).param("available", available)
                    .param("productId", item.productId()).param("size", item.size()).update();
            jdbc.sql("""
                    INSERT INTO inventory_logs(product_id,size,change_type,quantity_change,quantity_before,
                        quantity_after,reference_type,created_by,created_at)
                    VALUES(:productId,:size,'RESERVATION_RELEASE',:change,:before,:after,:reference,NULL,UTC_TIMESTAMP())
                    """).param("productId", item.productId()).param("size", item.size())
                    .param("change", item.quantity()).param("before", stock.quantity()).param("after", after)
                    .param("reference", "order:" + order.orderNumber()).update();
        }
    }

    private void releaseCoupon(CancellationRow order) {
        if (order.couponId() == null) return;
        int released = jdbc.sql("DELETE FROM coupon_usage WHERE order_id=:orderId")
                .param("orderId", order.id()).update();
        if (released > 0) {
            jdbc.sql("""
                    UPDATE coupons SET current_usage_count=GREATEST(current_usage_count-:released,0),
                        updated_at=UTC_TIMESTAMP() WHERE id=:couponId
                    """).param("released", released).param("couponId", order.couponId()).update();
        }
    }

    private void requireDirectCancellation(CancellationRow order) {
        if (DIRECTLY_CANCELLABLE.contains(order.status())) return;
        if ("CONFIRMED".equals(order.status()) || "PROCESSING".equals(order.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_REFUND_REQUIRED",
                    "A paid order cannot be cancelled until the refund workflow is available");
        }
        throw new ApiException(HttpStatus.CONFLICT, "ORDER_NOT_CANCELLABLE",
                "This order can no longer be cancelled");
    }

    private CancellationEligibilityResponse eligibility(CancellationRow order) {
        if (DIRECTLY_CANCELLABLE.contains(order.status())) {
            return new CancellationEligibilityResponse(order.orderNumber(), OrderStatus.valueOf(order.status()),
                    true, null, null);
        }
        String code = List.of("CONFIRMED", "PROCESSING").contains(order.status())
                ? "PAYMENT_REFUND_REQUIRED" : "ORDER_NOT_CANCELLABLE";
        String message = "CANCELLED".equals(order.status()) ? "This order is already cancelled"
                : "PAYMENT_REFUND_REQUIRED".equals(code)
                ? "A paid order requires the refund workflow before cancellation"
                : "This order can no longer be cancelled";
        return new CancellationEligibilityResponse(order.orderNumber(), OrderStatus.valueOf(order.status()),
                false, code, message);
    }

    private CancellationRow ownedOrder(long customerId, String orderNumber, boolean lock) {
        return jdbc.sql(orderSql() + " WHERE user_id=:userId AND order_number=:orderNumber" + (lock ? " FOR UPDATE" : ""))
                .param("userId", customerId).param("orderNumber", orderNumber)
                .query((rs, rowNum) -> row(rs)).optional().orElseThrow(() -> ApiException.notFound("Order"));
    }

    private CancellationRow orderById(long orderId, boolean lock) {
        return jdbc.sql(orderSql() + " WHERE id=:id" + (lock ? " FOR UPDATE" : ""))
                .param("id", orderId).query((rs, rowNum) -> row(rs)).optional()
                .orElseThrow(() -> ApiException.notFound("Order"));
    }

    private CancellationRow orderByNumber(String orderNumber, boolean lock) {
        return jdbc.sql(orderSql() + " WHERE order_number=:orderNumber" + (lock ? " FOR UPDATE" : ""))
                .param("orderNumber", orderNumber).query((rs, rowNum) -> row(rs)).optional()
                .orElseThrow(() -> ApiException.notFound("Order"));
    }

    private String orderSql() {
        return "SELECT id,user_id,order_number,status,coupon_id,payment_expires_at,reservation_released_at FROM orders";
    }

    private CancellationRow row(ResultSet rs) throws SQLException {
        Long couponId = rs.getLong("coupon_id");
        if (rs.wasNull()) couponId = null;
        var releasedAt = rs.getTimestamp("reservation_released_at");
        return new CancellationRow(rs.getLong("id"), rs.getLong("user_id"), rs.getString("order_number"),
                rs.getString("status"), couponId, rs.getTimestamp("payment_expires_at").toInstant(),
                releasedAt == null ? null : releasedAt.toInstant());
    }

    private boolean hasOpenProviderAttempt(long orderId) {
        return jdbc.sql("""
                SELECT EXISTS(SELECT 1 FROM payments WHERE order_id=:orderId
                    AND gateway_order_id IS NOT NULL AND status IN ('INITIATED','PENDING'))
                """).param("orderId", orderId).query(Boolean.class).single();
    }

    public record CancellationCandidate(long orderId, long customerId, String orderNumber,
                                        String status, boolean openProviderAttempt) {}
    private record CancellationRow(long id, long userId, String orderNumber, String status, Long couponId,
                                   Instant paymentExpiresAt, Instant reservationReleasedAt) {}
    private record ReservedItem(long productId, String size, int quantity) {}
    private record Stock(int quantity, boolean available) {}
}
