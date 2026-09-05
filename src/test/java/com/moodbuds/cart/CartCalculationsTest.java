package com.moodbuds.cart;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class CartCalculationsTest {
    @Test
    void calculatesGstInPaiseWithHalfUpRounding() {
        assertThat(CartCalculations.gst(10_000, new BigDecimal("5.00"))).isEqualTo(500);
        assertThat(CartCalculations.gst(999, new BigDecimal("12.00"))).isEqualTo(120);
        assertThat(CartCalculations.gst(0, new BigDecimal("5.00"))).isZero();
        assertThat(CartCalculations.gst(1_000, null)).isZero();
    }
}
