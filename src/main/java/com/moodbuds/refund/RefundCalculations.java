package com.moodbuds.refund;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class RefundCalculations {
    private RefundCalculations() {}

    public static long itemAmount(long refundableLineAmount, int purchasedQuantity,
                                  int alreadyAllocatedQuantity, long alreadyAllocatedAmount,
                                  int returnQuantity) {
        if (refundableLineAmount < 0 || purchasedQuantity <= 0 || alreadyAllocatedQuantity < 0
                || returnQuantity <= 0 || alreadyAllocatedQuantity + returnQuantity > purchasedQuantity) {
            throw new IllegalArgumentException("Invalid refund allocation inputs");
        }
        if (alreadyAllocatedQuantity + returnQuantity == purchasedQuantity) {
            return Math.max(refundableLineAmount - alreadyAllocatedAmount, 0);
        }
        return BigDecimal.valueOf(refundableLineAmount).multiply(BigDecimal.valueOf(returnQuantity))
                .divide(BigDecimal.valueOf(purchasedQuantity), 0, RoundingMode.DOWN).longValueExact();
    }
}
