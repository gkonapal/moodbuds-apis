package com.moodbuds.coupon.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.moodbuds.cart.api.CartDtos.CartResponse;
import com.moodbuds.customer.api.CustomerDtos.AddressResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class CouponDtos {
    private CouponDtos() {}

    public enum CouponType { FLAT, PERCENTAGE }
    public enum CouponAudience { PUBLIC, PRIVATE_CODE, ASSIGNED_USERS }

    public record ApplyCouponRequest(
            @NotBlank @Size(max = 50) String code) {}

    public record PublicCouponResponse(String code, String description, CouponType type,
                                       BigDecimal discountValue, long minOrderValue,
                                       Long maxDiscountAmount, Instant validFrom, Instant validUntil) {}

    public record CustomerCouponResponse(String code, String description, CouponType type,
                                         BigDecimal discountValue, long minOrderValue,
                                         Long maxDiscountAmount, Instant validFrom, Instant validUntil,
                                         CouponAudience audience, boolean firstOrderOnly,
                                         boolean showOnHomepage, int remainingUses) {}

    public record AppliedCouponResponse(long id, String code, String description, CouponType type,
                                        BigDecimal discountValue, long discountAmount,
                                        long minOrderValue, Long maxDiscountAmount,
                                        Instant validFrom, Instant validUntil) {}

    public record CouponTotals(long mrpSubtotal, long productDiscount, long sellingSubtotal,
                               long couponDiscount, long taxableSubtotal, long gstAmount,
                               Long shippingCost, long grandTotal) {}

    public record CartCouponResponse(boolean applied, AppliedCouponResponse coupon,
                                     CouponTotals totals, CartResponse cart) {}

    public record CheckoutPreviewRequest(@Positive long addressId) {}

    public record CheckoutPreviewResponse(boolean cartValid, boolean addressValid,
                                          boolean readyForOrderCreation, List<String> blockers,
                                          List<String> pendingIntegrations,
                                          AddressResponse shippingAddress,
                                          AppliedCouponResponse coupon, CouponTotals totals,
                                          CartResponse cart) {}
}
