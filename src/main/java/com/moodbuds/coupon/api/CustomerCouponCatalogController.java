package com.moodbuds.coupon.api;

import java.util.List;

import com.moodbuds.coupon.CouponService;
import com.moodbuds.customer.CurrentCustomer;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customer/coupons")
public class CustomerCouponCatalogController {
    private final CouponService coupons;
    public CustomerCouponCatalogController(CouponService coupons){this.coupons=coupons;}

    @GetMapping
    List<CouponDtos.CustomerCouponResponse> list(@AuthenticationPrincipal Jwt jwt){
        return coupons.customerCoupons(CurrentCustomer.id(jwt),false);
    }

    @GetMapping("/homepage")
    CouponDtos.CustomerCouponResponse homepage(@AuthenticationPrincipal Jwt jwt){
        return coupons.customerCoupons(CurrentCustomer.id(jwt),true).stream().findFirst()
                .orElseThrow(()->com.moodbuds.common.ApiException.notFound("Homepage coupon"));
    }
}
