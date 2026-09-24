package com.moodbuds.coupon;

import static com.moodbuds.coupon.api.CouponDtos.*;

import java.util.ArrayList;
import java.util.List;

import com.moodbuds.cart.CartService;
import com.moodbuds.customer.CustomerProfileService;
import com.moodbuds.payment.RazorpayGateway;
import com.moodbuds.shipping.ShippingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckoutPreviewService {
    private final CartService carts;
    private final CouponService coupons;
    private final CustomerProfileService customers;
    private final RazorpayGateway payments;
    private final ShippingService shipping;

    public CheckoutPreviewService(CartService carts, CouponService coupons, CustomerProfileService customers,
                                  RazorpayGateway payments, ShippingService shipping) {
        this.carts = carts;
        this.coupons = coupons;
        this.customers = customers;
        this.payments = payments;
        this.shipping = shipping;
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
        boolean paymentAvailable = payments.available();
        if (!paymentAvailable) blockers.add("PAYMENT_PROVIDER_UNAVAILABLE");
        var shippingQuote = cartValid ? shipping.quoteCart(customerId, request.addressId()) : null;
        boolean shippingAvailable = shippingQuote != null && shippingQuote.serviceable();
        if (!shippingAvailable) blockers.add("PINCODE_NOT_SERVICEABLE");
        var base = couponState.totals();
        long shippingCost = shippingQuote == null ? 0 : shippingQuote.customerShippingCost();
        var totals = new CouponTotals(base.mrpSubtotal(), base.productDiscount(), base.sellingSubtotal(),
                base.couponDiscount(), base.taxableSubtotal(), base.gstAmount(), shippingCost,
                base.grandTotal() + shippingCost);
        var pending = new ArrayList<String>();
        if (!paymentAvailable) pending.add("PAYMENT_PROVIDER");
        return new CheckoutPreviewResponse(cartValid, true, cartValid && paymentAvailable && shippingAvailable,
                List.copyOf(blockers), List.copyOf(pending), address, shippingQuote,
                couponState.coupon(), totals, couponState.cart());
    }
}
