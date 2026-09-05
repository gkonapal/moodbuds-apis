package com.moodbuds.coupon.api;

import com.moodbuds.coupon.CheckoutPreviewService;
import com.moodbuds.customer.CurrentCustomer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customer/checkout")
@Tag(name = "Customer Checkout Preview")
public class CheckoutPreviewController {
    private final CheckoutPreviewService checkout;

    public CheckoutPreviewController(CheckoutPreviewService checkout) {
        this.checkout = checkout;
    }

    @PostMapping("/preview")
    @Operation(summary = "Validate cart, coupon, and owned shipping address before provider checkout")
    CouponDtos.CheckoutPreviewResponse preview(@AuthenticationPrincipal Jwt jwt,
                                               @Valid @RequestBody CouponDtos.CheckoutPreviewRequest request) {
        return checkout.preview(CurrentCustomer.id(jwt), request);
    }
}
