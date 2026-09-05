package com.moodbuds.refund.api;

import static com.moodbuds.refund.api.RefundDtos.*;

import java.util.Map;

import com.moodbuds.audit.AuditService;
import com.moodbuds.auth.CurrentAdmin;
import com.moodbuds.common.PageResponse;
import com.moodbuds.refund.RefundService;
import com.moodbuds.refund.RefundStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin Refunds")
public class AdminRefundController {
    private final RefundService refunds;
    private final RefundStore store;
    private final AuditService audit;

    public AdminRefundController(RefundService refunds, RefundStore store, AuditService audit) {
        this.refunds = refunds;
        this.store = store;
        this.audit = audit;
    }

    @PostMapping("/returns/{returnId}/refunds")
    @PreAuthorize("hasAuthority('returns.manage') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Initiate or safely retry the Razorpay refund for a quality-approved return")
    RefundResponse initiate(@PathVariable long returnId,
                            @RequestHeader("Idempotency-Key") @NotBlank @Size(min = 10, max = 128)
                            @Pattern(regexp = "[A-Za-z0-9_-]+") String idempotencyKey,
                            @Valid @RequestBody InitiateRefundRequest request,
                            @AuthenticationPrincipal Jwt jwt) {
        var result = refunds.initiate(returnId, idempotencyKey, request);
        audit.record(CurrentAdmin.id(jwt), "refund.initiated", "refund", result.id(), null,
                Map.of("returnRequestId", returnId, "amount", result.amount()));
        return result;
    }

    @GetMapping("/refunds")
    @PreAuthorize("hasAuthority('returns.read') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "List refunds")
    PageResponse<RefundResponse> list(@RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return refunds.adminRefunds(page, size);
    }

    @GetMapping("/refunds/{refundId}")
    @PreAuthorize("hasAuthority('returns.read') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Get refund status")
    RefundResponse get(@PathVariable long refundId) { return refunds.adminRefund(refundId); }

    @PostMapping("/refunds/{refundId}/reconcile")
    @PreAuthorize("hasAuthority('returns.manage') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Reconcile or idempotently retry a Razorpay refund")
    RefundResponse reconcile(@PathVariable long refundId, @AuthenticationPrincipal Jwt jwt) {
        var result = refunds.reconcile(refundId);
        audit.record(CurrentAdmin.id(jwt), "refund.reconciled", "refund", refundId, null,
                Map.of("status", result.status().name()));
        return result;
    }

    @PatchMapping("/returns/{returnId}/items/{returnItemId}/inspection")
    @PreAuthorize("hasAuthority('returns.manage') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Record the warehouse condition of a received return item")
    Map<String, Object> inspect(@PathVariable long returnId, @PathVariable long returnItemId,
                                @Valid @RequestBody InspectReturnItemRequest request,
                                @AuthenticationPrincipal Jwt jwt) {
        var result = store.inspect(returnId, returnItemId, request.condition());
        audit.record(CurrentAdmin.id(jwt), "return.item_inspected", "return_item", returnItemId,
                null, Map.of("condition", request.condition().name()));
        return result;
    }
}
