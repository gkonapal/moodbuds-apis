package com.moodbuds.coupon;

import static com.moodbuds.coupon.api.CouponDtos.*;

import java.util.ArrayList;
import java.util.List;

import com.moodbuds.cart.CartService;
import com.moodbuds.customer.CustomerProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckoutPreviewService {
    private final CartService carts;
    private final CouponService coupons;
    private final CustomerProfileService customers;

    public CheckoutPreviewService(CartService carts, CouponService coupons, CustomerProfileService customers) {
        this.carts = carts;
        this.coupons = coupons;
        this.customers = customers;
    }

    @Transactional
    public CheckoutPreviewResponse preview(long customerId, CheckoutPreviewRequest request) {
        var address = customers.address(customerId, request.addressId());
        var validation = carts.validate(customerId);
        var couponState = coupons.revalidate(customerId);
        var blockers = new ArrayList<String>();
        validation.issues().stream().filter(issue -> issue.blocking()).map(issue -> issue.code()).forEach(blockers::add);
        if (validation.cart().items().isEmpty()) blockers.add("EMPTY_CART");
        boolean cartValid = validation.valid() && !validation.cart().items().isEmpty();
        return new CheckoutPreviewResponse(cartValid, true, cartValid, List.copyOf(blockers),
                List.of("SHIPPING_PROVIDER", "PAYMENT_PROVIDER"), address,
                couponState.coupon(), couponState.totals(), couponState.cart());
    }
}
