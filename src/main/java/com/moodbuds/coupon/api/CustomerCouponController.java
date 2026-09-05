package com.moodbuds.coupon.api;

import static com.moodbuds.coupon.api.CouponDtos.*;

import com.moodbuds.coupon.CouponService;
import com.moodbuds.customer.CurrentCustomer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customer/cart/coupon")
@Tag(name = "Customer Cart Coupons")
public class CustomerCouponController {
    private final CouponService coupons;

    public CustomerCouponController(CouponService coupons) {
        this.coupons = coupons;
    }

    @GetMapping
    @Operation(summary = "Get the cart's applied coupon and payable totals")
    CartCouponResponse get(@AuthenticationPrincipal Jwt jwt) {
        return coupons.get(CurrentCustomer.id(jwt));
    }

    @PostMapping
    @Operation(summary = "Validate and apply a coupon code to the cart")
    CartCouponResponse apply(@AuthenticationPrincipal Jwt jwt,
                             @Valid @RequestBody ApplyCouponRequest request) {
        return coupons.apply(CurrentCustomer.id(jwt), request);
    }

    @PostMapping("/validate")
    @Operation(summary = "Revalidate the applied coupon and refresh its discount")
    CartCouponResponse validate(@AuthenticationPrincipal Jwt jwt) {
        return coupons.revalidate(CurrentCustomer.id(jwt));
    }

    @DeleteMapping
    @Operation(summary = "Remove the applied coupon from the cart")
    CartCouponResponse remove(@AuthenticationPrincipal Jwt jwt) {
        return coupons.remove(CurrentCustomer.id(jwt));
    }
}
