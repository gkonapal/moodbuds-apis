package com.moodbuds.coupon;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

class CouponCalculationsTest {
    @Test
    void convertsFlatRupeesToPaise() {
        assertThat(CouponCalculations.discount("FLAT", new BigDecimal("125.50"), 20_000, null))
                .isEqualTo(12_550);
    }

    @Test
    void capsPercentageDiscountAndNeverExceedsSubtotal() {
        assertThat(CouponCalculations.discount("PERCENTAGE", new BigDecimal("20"), 10_000, 1_500L))
                .isEqualTo(1_500);
        assertThat(CouponCalculations.discount("FLAT", new BigDecimal("500"), 20_000, null))
                .isEqualTo(20_000);
    }

    @Test
    void calculatesGstAfterProportionalCouponAllocation() {
        var lines = List.of(
                new CouponCalculations.TaxLine(10_000, new BigDecimal("5")),
                new CouponCalculations.TaxLine(20_000, new BigDecimal("12")));

        assertThat(CouponCalculations.gstAfterDiscount(lines, 3_000)).isEqualTo(2_358);
    }

    @Test
    void handlesZeroTaxableCart() {
        assertThat(CouponCalculations.gstAfterDiscount(List.of(), 100)).isZero();
    }

    @Test
    void allocatesEveryDiscountPaiseWithoutExceedingLines() {
        assertThat(CouponCalculations.allocateDiscount(List.of(101L, 202L, 303L), 101L))
                .containsExactly(16L, 34L, 51L);
        assertThat(CouponCalculations.allocateDiscount(List.of(1L, 1L, 1L), 3L))
                .containsExactly(1L, 1L, 1L);
    }
}
