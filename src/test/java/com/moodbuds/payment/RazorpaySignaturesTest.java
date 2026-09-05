package com.moodbuds.payment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RazorpaySignaturesTest {
    @Test
    void verifiesExpectedPaymentSignatureAndRejectsTampering() {
        String signature = RazorpaySignatures.sign("order_123|pay_456", "test-secret");

        assertThat(RazorpaySignatures.verifyPayment("order_123", "pay_456", signature, "test-secret")).isTrue();
        assertThat(RazorpaySignatures.verifyPayment("order_123", "pay_changed", signature, "test-secret")).isFalse();
        assertThat(RazorpaySignatures.verifyPayment("order_123", "pay_456", "not-hex", "test-secret")).isFalse();
    }
}
