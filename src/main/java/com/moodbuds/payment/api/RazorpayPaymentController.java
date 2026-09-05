package com.moodbuds.payment.api;

import static com.moodbuds.payment.api.PaymentDtos.*;

import com.moodbuds.customer.CurrentCustomer;
import com.moodbuds.payment.RazorpayPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/customer/orders/{orderNumber}/payments/razorpay")
@Tag(name = "Customer Razorpay Payments")
public class RazorpayPaymentController {
    private final RazorpayPaymentService payments;

    public RazorpayPaymentController(RazorpayPaymentService payments) {
        this.payments = payments;
    }

    @PostMapping
    @Operation(summary = "Create or reuse a Razorpay Checkout order for a finalized MoodBuds order")
    RazorpayInitiationResponse initiate(@AuthenticationPrincipal Jwt jwt, @PathVariable String orderNumber,
                                        @RequestHeader("Idempotency-Key")
                                        @NotBlank @Size(min = 8, max = 128) String idempotencyKey) {
        return payments.initiate(CurrentCustomer.id(jwt), orderNumber, idempotencyKey);
    }

    @PostMapping("/verify")
    @Operation(summary = "Verify the Razorpay Checkout signature and captured payment")
    PaymentStatusResponse verify(@AuthenticationPrincipal Jwt jwt, @PathVariable String orderNumber,
                                 @Valid @RequestBody RazorpayVerifyRequest request) {
        return payments.verify(CurrentCustomer.id(jwt), orderNumber, request);
    }

    @GetMapping
    @Operation(summary = "Get locally persisted Razorpay payment status")
    PaymentStatusResponse status(@AuthenticationPrincipal Jwt jwt, @PathVariable String orderNumber) {
        return payments.status(CurrentCustomer.id(jwt), orderNumber);
    }

    @PostMapping("/reconcile")
    @Operation(summary = "Reconcile an uncertain payment using Razorpay's server API")
    PaymentStatusResponse reconcile(@AuthenticationPrincipal Jwt jwt, @PathVariable String orderNumber) {
        return payments.reconcile(CurrentCustomer.id(jwt), orderNumber);
    }
}
