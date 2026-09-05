package com.moodbuds.coupon.api;

import java.util.List;

import com.moodbuds.coupon.CouponService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/coupons")
@Tag(name = "Public Coupons")
public class PublicCouponController {
    private final CouponService coupons;

    public PublicCouponController(CouponService coupons) {
        this.coupons = coupons;
    }

    @GetMapping
    @Operation(summary = "List currently usable public coupons")
    List<CouponDtos.PublicCouponResponse> list() {
        return coupons.publicCoupons();
    }

    @GetMapping("/{code}")
    @Operation(summary = "Get a currently usable public coupon by code")
    CouponDtos.PublicCouponResponse get(@PathVariable String code) {
        return coupons.publicCoupon(code);
    }
}
