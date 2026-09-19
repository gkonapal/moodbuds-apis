package com.moodbuds.cart;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class CartCalculations {
    private CartCalculations() {}

    public static long gst(long taxableAmountPaise, BigDecimal ratePercentage) {
        if (taxableAmountPaise <= 0 || ratePercentage == null || ratePercentage.signum() <= 0) return 0;
        return BigDecimal.valueOf(taxableAmountPaise).multiply(ratePercentage)
                .divide(BigDecimal.valueOf(100).add(ratePercentage), 0, RoundingMode.HALF_UP).longValueExact();
    }
}
