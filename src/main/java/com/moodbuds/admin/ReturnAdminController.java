package com.moodbuds.admin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.moodbuds.audit.AuditService;
import com.moodbuds.auth.CurrentAdmin;
import com.moodbuds.common.ApiException;
import com.moodbuds.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.moodbuds.shipping.ShippingService;

@Validated
@RestController
@RequestMapping("/api/v1/admin/returns")
public class ReturnAdminController {
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "REQUESTED", Set.of("APPROVED", "REJECTED"),
            "APPROVED", Set.of("PICKUP_SCHEDULED", "ITEM_RECEIVED"),
            "PICKUP_SCHEDULED", Set.of("ITEM_RECEIVED"),
            "ITEM_RECEIVED", Set.of("QUALITY_CHECK_PASSED", "QUALITY_CHECK_FAILED"),
            "QUALITY_CHECK_FAILED", Set.of("CLOSED"));

    private final JdbcClient jdbc;
    private final AuditService audit;
    private final ShippingService shipping;

    public ReturnAdminController(JdbcClient jdbc, AuditService audit, ShippingService shipping) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.shipping = shipping;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('returns.read') or hasRole('SUPER_ADMIN')")
    Object list(@RequestParam(required = false) String status,
                @RequestParam(defaultValue = "0") int page,
                @RequestParam(defaultValue = "20") int size) {
        int boundedSize = Math.min(Math.max(size, 1), 100);
        int boundedPage = Math.max(page, 0);
        String where = status == null || status.isBlank() ? "" : " WHERE rr.status=:status";
        var query = jdbc.sql("""
                SELECT rr.id,rr.status,rr.reason,rr.reason_description,rr.admin_notes,rr.rejection_reason,
                       rr.requested_at,rr.updated_at,o.order_number,
                       CONCAT(u.first_name,' ',u.last_name) customer_name,u.email customer_email,
                       (SELECT COUNT(*) FROM return_items ri WHERE ri.return_request_id=rr.id) item_count,
                       (SELECT COALESCE(SUM(ri.quantity_to_return),0) FROM return_items ri
                            WHERE ri.return_request_id=rr.id) total_quantity,
                       (SELECT oi.product_snapshot FROM return_items ri
                            JOIN order_items oi ON oi.id=ri.order_item_id
                            WHERE ri.return_request_id=rr.id ORDER BY ri.id LIMIT 1) product_snapshot,
                       (SELECT r.status FROM refunds r WHERE r.return_request_id=rr.id
                            ORDER BY r.id DESC LIMIT 1) refund_status,
                       (SELECT r.amount FROM refunds r WHERE r.return_request_id=rr.id
                            ORDER BY r.id DESC LIMIT 1) refund_amount
                FROM return_requests rr JOIN orders o ON o.id=rr.order_id
                JOIN users u ON u.id=rr.user_id
                """ + where + " ORDER BY rr.id DESC LIMIT :limit OFFSET :offset")
                .param("limit", boundedSize).param("offset", boundedPage * boundedSize);
        var count = jdbc.sql("SELECT COUNT(*) FROM return_requests rr" + where);
        if (!where.isEmpty()) {
            query = query.param("status", status.toUpperCase());
            count = count.param("status", status.toUpperCase());
        }
        return PageResponse.of(query.query().listOfRows(), boundedPage, boundedSize,
                count.query(Long.class).single());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('returns.read') or hasRole('SUPER_ADMIN')")
    Object get(@PathVariable long id) {
        var result = new LinkedHashMap<>(jdbc.sql("""
                SELECT rr.*,o.order_number,o.status order_status,
                       CONCAT(u.first_name,' ',u.last_name) customer_name,u.email customer_email,u.mobile customer_mobile
                FROM return_requests rr JOIN orders o ON o.id=rr.order_id
                JOIN users u ON u.id=rr.user_id WHERE rr.id=:id
                """).param("id", id).query().listOfRows().stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Return request")));
        result.put("items", jdbc.sql("""
                SELECT ri.id,ri.order_item_id,ri.quantity_to_return,ri.reason,ri.condition_on_receipt,
                       oi.product_id,oi.product_snapshot,oi.size,oi.unit_price,oi.line_total
                FROM return_items ri JOIN order_items oi ON oi.id=ri.order_item_id
                WHERE ri.return_request_id=:id ORDER BY ri.id
                """).param("id", id).query().listOfRows());
        result.put("history", jdbc.sql("""
                SELECT id,from_status,to_status,notes,reason,actor_type,created_at
                FROM return_status_history WHERE return_request_id=:id ORDER BY created_at,id
                """).param("id", id).query().listOfRows());
        result.put("refund", jdbc.sql("""
                SELECT id,amount,currency,status,gateway_refund_id,provider_reference,
                       failure_code,failure_description,initiated_at,completed_at,updated_at
                FROM refunds WHERE return_request_id=:id ORDER BY id DESC LIMIT 1
                """).param("id", id).query().listOfRows().stream().findFirst().orElse(null));
        result.put("reverseShipment", shipping.forReturnAdmin(id));
        return result;
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('returns.manage') or hasRole('SUPER_ADMIN')")
    @Transactional
    Object status(@PathVariable long id, @Valid @RequestBody StatusChange request,
                  @AuthenticationPrincipal Jwt jwt) {
        var old = jdbc.sql("SELECT id,order_id,status FROM return_requests WHERE id=:id FOR UPDATE")
                .param("id", id).query().listOfRows().stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Return request"));
        String from = String.valueOf(old.get("status"));
        String to = request.status().trim().toUpperCase();
        if (!TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw ApiException.badRequest("INVALID_RETURN_TRANSITION",
                    "Return cannot move from " + from + " to " + to);
        }
        String reason = clean(request.reason());
        String notes = clean(request.notes());
        if ("REJECTED".equals(to) && reason == null) {
            throw ApiException.badRequest("RETURN_REJECTION_REASON_REQUIRED",
                    "A rejection reason is required");
        }
        if ("QUALITY_CHECK_PASSED".equals(to)) {
            long pending = jdbc.sql("""
                    SELECT COUNT(*) FROM return_items
                    WHERE return_request_id=:id AND condition_on_receipt IS NULL
                    """).param("id", id).query(Long.class).single();
            if (pending > 0) {
                throw ApiException.badRequest("RETURN_INSPECTION_INCOMPLETE",
                        "Every returned item must be inspected before quality checking passes");
            }
        }
        if ("PICKUP_SCHEDULED".equals(to)) shipping.scheduleReverse(id);
        jdbc.sql("""
                UPDATE return_requests SET status=:status,
                    admin_notes=COALESCE(:notes,admin_notes),
                    rejection_reason=CASE WHEN :status='REJECTED' THEN :reason ELSE rejection_reason END,
                    updated_at=UTC_TIMESTAMP() WHERE id=:id
                """).param("status", to).param("notes", notes).param("reason", reason).param("id", id).update();
        long adminId = CurrentAdmin.id(jwt);
        jdbc.sql("""
                INSERT INTO return_status_history(return_request_id,from_status,to_status,notes,reason,
                    actor_type,actor_id,created_at)
                VALUES(:id,:from,:to,:notes,:reason,'ADMIN',:admin,UTC_TIMESTAMP())
                """).param("id", id).param("from", from).param("to", to).param("notes", notes)
                .param("reason", reason).param("admin", adminId).update();
        syncOrderStatus(((Number) old.get("order_id")).longValue(), id, to, notes, reason, adminId);
        audit.record(adminId, "return.status_updated", "return_request", id, old,
                Map.of("status", to));
        return get(id);
    }

    private void syncOrderStatus(long orderId, long returnId, String returnStatus,
                                 String notes, String reason, long adminId) {
        String target = switch (returnStatus) {
            case "PICKUP_SCHEDULED" -> "RETURN_PICKUP_SCHEDULED";
            case "ITEM_RECEIVED", "QUALITY_CHECK_PASSED", "QUALITY_CHECK_FAILED" -> "RETURN_RECEIVED";
            case "REJECTED", "CLOSED" -> hasAnotherActiveReturn(orderId, returnId) ? null : "DELIVERED";
            default -> null;
        };
        if (target == null) return;
        String current = jdbc.sql("SELECT status FROM orders WHERE id=:id FOR UPDATE")
                .param("id", orderId).query(String.class).single();
        if (target.equals(current)) return;
        boolean allowed = switch (target) {
            case "RETURN_PICKUP_SCHEDULED" -> "RETURN_INITIATED".equals(current);
            case "RETURN_RECEIVED" -> Set.of("RETURN_INITIATED", "RETURN_PICKUP_SCHEDULED").contains(current);
            case "DELIVERED" -> Set.of("RETURN_INITIATED", "RETURN_PICKUP_SCHEDULED", "RETURN_RECEIVED").contains(current);
            default -> false;
        };
        if (!allowed) return;
        String message = reason != null ? reason : notes != null ? notes : "Return updated to " + returnStatus;
        jdbc.sql("UPDATE orders SET status=:status,updated_at=UTC_TIMESTAMP() WHERE id=:id")
                .param("status", target).param("id", orderId).update();
        jdbc.sql("""
                INSERT INTO order_status_history(order_id,from_status,to_status,notes,changed_by_id,created_at)
                VALUES(:id,:from,:to,:notes,:admin,UTC_TIMESTAMP())
                """).param("id", orderId).param("from", current).param("to", target)
                .param("notes", message).param("admin", adminId).update();
    }

    private boolean hasAnotherActiveReturn(long orderId, long returnId) {
        long count = jdbc.sql("""
                SELECT COUNT(*) FROM return_requests WHERE order_id=:orderId AND id<>:returnId
                  AND status NOT IN ('REJECTED','CLOSED','COMPLETED')
                """).param("orderId", orderId).param("returnId", returnId).query(Long.class).single();
        return count > 0;
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record StatusChange(@NotBlank @Size(max = 50) String status,
                               @Size(max = 2000) String notes,
                               @Size(max = 500) String reason) {}
}
