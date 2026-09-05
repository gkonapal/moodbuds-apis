package com.moodbuds.payment.api;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class PaymentDtos {
    private PaymentDtos() {}

    public record RazorpayVerifyRequest(
            @NotBlank @Size(max = 255) String razorpayOrderId,
            @NotBlank @Size(max = 255) String razorpayPaymentId,
            @NotBlank @Size(max = 255) String razorpaySignature) {}

    public record CheckoutPrefill(String name, String email, String contact) {}
    public record CheckoutTheme(String color) {}

    public record RazorpayInitiationResponse(String orderNumber, long localPaymentId,
                                             String razorpayOrderId, String keyId,
                                             long amount, String currency, String paymentStatus,
                                             String name, String description,
                                             CheckoutPrefill prefill, CheckoutTheme theme,
                                             Instant paymentExpiresAt) {}

    public record PaymentStatusResponse(String orderNumber, String orderStatus, String gateway,
                                        String razorpayOrderId, String razorpayPaymentId,
                                        String paymentMethod, String paymentStatus,
                                        long amount, String currency, boolean canRetry,
                                        Instant initiatedAt, Instant completedAt,
                                        Instant paymentExpiresAt) {}
}
