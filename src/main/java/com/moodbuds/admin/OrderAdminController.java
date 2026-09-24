package com.moodbuds.admin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.moodbuds.audit.AuditService;
import com.moodbuds.auth.CurrentAdmin;
import com.moodbuds.common.ApiException;
import com.moodbuds.common.PageResponse;
import com.moodbuds.order.OrderCancellationService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import com.moodbuds.shipping.ShippingService;

@RestController
@RequestMapping("/api/v1/admin/orders")
public class OrderAdminController {
    private static final Map<String, Set<String>> TRANSITIONS = Map.ofEntries(
            Map.entry("PENDING_PAYMENT", Set.of("PAYMENT_FAILED", "CANCELLED")),
            Map.entry("PAYMENT_FAILED", Set.of("PENDING_PAYMENT", "CANCELLED")),
            Map.entry("PROCESSING", Set.of("CONFIRMED")), Map.entry("CONFIRMED", Set.of("PACKED")),
            Map.entry("PACKED", Set.of("SHIPPED")), Map.entry("SHIPPED", Set.of("OUT_FOR_DELIVERY")),
            Map.entry("OUT_FOR_DELIVERY", Set.of("DELIVERED")), Map.entry("DELIVERED", Set.of("RETURN_INITIATED")),
            Map.entry("RETURN_INITIATED", Set.of("RETURN_PICKUP_SCHEDULED", "RETURN_RECEIVED")),
            Map.entry("RETURN_PICKUP_SCHEDULED", Set.of("RETURN_RECEIVED")),
            Map.entry("RETURN_RECEIVED", Set.of("REFUND_INITIATED")),
            Map.entry("REFUND_INITIATED", Set.of("REFUNDED")));

    private final JdbcClient jdbc;
    private final AuditService audit;
    private final OrderCancellationService cancellations;
    private final ShippingService shipping;

    public OrderAdminController(JdbcClient jdbc, AuditService audit, OrderCancellationService cancellations,
                                ShippingService shipping) {
        this.jdbc = jdbc; this.audit = audit; this.cancellations = cancellations; this.shipping = shipping;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('orders.read') or hasRole('SUPER_ADMIN')")
    Object list(@RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page,
                @RequestParam(defaultValue = "20") int size) {
        int boundedSize = Math.min(Math.max(size, 1), 100), boundedPage = Math.max(page, 0);
        String where = status == null || status.isBlank() ? "" : " WHERE o.status=:status";
        var query = jdbc.sql("""
                SELECT o.order_number,o.status,o.payment_method,o.total_amount,o.created_at,o.updated_at,
                       CONCAT(u.first_name,' ',u.last_name) customer_name,u.email customer_email,
                       (SELECT p.status FROM payments p WHERE p.order_id=o.id ORDER BY p.id DESC LIMIT 1) payment_status,
                       (SELECT COALESCE(SUM(oi.quantity),0) FROM order_items oi WHERE oi.order_id=o.id) item_count
                       ,(SELECT s.status FROM shipments s WHERE s.order_id=o.id AND s.direction='FORWARD'
                           ORDER BY s.id DESC LIMIT 1) shipping_status
                       ,(SELECT s.estimated_delivery_date FROM shipments s WHERE s.order_id=o.id AND s.direction='FORWARD'
                           ORDER BY s.id DESC LIMIT 1) expected_delivery_date
                FROM orders o JOIN users u ON u.id=o.user_id
                """ + where + " ORDER BY o.id DESC LIMIT :limit OFFSET :offset")
                .param("limit", boundedSize).param("offset", boundedPage * boundedSize);
        var count = jdbc.sql("SELECT COUNT(*) FROM orders o" + where);
        if (!where.isEmpty()) { query = query.param("status", status); count = count.param("status", status); }
        return PageResponse.of(query.query().listOfRows(), boundedPage, boundedSize, count.query(Long.class).single());
    }

    @GetMapping("/{orderNumber}")
    @PreAuthorize("hasAuthority('orders.read') or hasRole('SUPER_ADMIN')")
    Object get(@PathVariable String orderNumber) {
        var result = new LinkedHashMap<>(jdbc.sql("""
                SELECT o.*,CONCAT(u.first_name,' ',u.last_name) customer_name,
                       u.email customer_email,u.mobile customer_mobile
                FROM orders o JOIN users u ON u.id=o.user_id WHERE o.order_number=:number
                """).param("number", orderNumber).query().listOfRows().stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Order")));
        long id = ((Number) result.get("id")).longValue();
        result.put("items", jdbc.sql("SELECT * FROM order_items WHERE order_id=:id ORDER BY id")
                .param("id", id).query().listOfRows());
        result.put("statusHistory", jdbc.sql("SELECT * FROM order_status_history WHERE order_id=:id ORDER BY id")
                .param("id", id).query().listOfRows());
        result.put("payments", jdbc.sql("SELECT * FROM payments WHERE order_id=:id ORDER BY id")
                .param("id", id).query().listOfRows());
        result.put("shipment", shipping.forOrderAdmin(orderNumber));
        return result;
    }

    @PatchMapping("/{orderNumber}/status")
    @PreAuthorize("hasAuthority('orders.manage') or hasRole('SUPER_ADMIN')")
    @Transactional
    Object status(@PathVariable String orderNumber, @RequestBody StatusChange request,
                  @AuthenticationPrincipal Jwt jwt) {
        var old = jdbc.sql("SELECT id,status FROM orders WHERE order_number=:number FOR UPDATE")
                .param("number", orderNumber).query().listOfRows().stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Order"));
        long id = ((Number) old.get("id")).longValue();
        String from = String.valueOf(old.get("status"));
        String to = request.status() == null ? "" : request.status().toUpperCase();
        if (!TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw ApiException.badRequest("INVALID_ORDER_TRANSITION", "Order cannot move from " + from + " to " + to);
        }
        if ("CANCELLED".equals(to)) {
            cancellations.cancelByAdmin(orderNumber, CurrentAdmin.id(jwt), request.notes());
        } else {
            jdbc.sql("UPDATE orders SET status=:status,updated_at=UTC_TIMESTAMP() WHERE id=:id")
                    .param("status", to).param("id", id).update();
            jdbc.sql("""
                    INSERT INTO order_status_history(order_id,from_status,to_status,notes,changed_by_id,created_at)
                    VALUES(:id,:from,:to,:notes,:admin,UTC_TIMESTAMP())
                    """).param("id", id).param("from", from).param("to", to).param("notes", request.notes())
                    .param("admin", CurrentAdmin.id(jwt)).update();
        }
        audit.record(CurrentAdmin.id(jwt), "order.status_updated", "order", id, old, Map.of("status", to));
        return get(orderNumber);
    }

    public record StatusChange(String status, String notes) {}
}
