package com.moodbuds.refund.api;

import static com.moodbuds.refund.api.RefundDtos.*;

import com.moodbuds.common.PageResponse;
import com.moodbuds.customer.CurrentCustomer;
import com.moodbuds.refund.RefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customer/refunds")
@Tag(name = "Customer Refunds")
public class CustomerRefundController {
    private final RefundService refunds;

    public CustomerRefundController(RefundService refunds) { this.refunds = refunds; }

    @GetMapping
    @Operation(summary = "List the authenticated customer's return refunds")
    PageResponse<RefundResponse> list(@AuthenticationPrincipal Jwt jwt,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return refunds.customerRefunds(CurrentCustomer.id(jwt), page, size);
    }

    @GetMapping("/{refundId}")
    @Operation(summary = "Get a customer-owned refund and its provider status")
    RefundResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable long refundId) {
        return refunds.customerRefund(CurrentCustomer.id(jwt), refundId);
    }
}
