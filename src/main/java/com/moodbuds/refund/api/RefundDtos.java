package com.moodbuds.refund.api;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

public final class RefundDtos {
    private RefundDtos() {}

    public enum RefundStatus { PENDING, INITIATED, PROCESSED, FAILED }
    public enum RefundSpeed { NORMAL, OPTIMUM }

    public record InitiateRefundRequest(@NotNull RefundSpeed speed) {
        public InitiateRefundRequest { speed = speed == null ? RefundSpeed.OPTIMUM : speed; }
    }

    public record RefundResponse(long id, String orderNumber, long returnRequestId,
                                 String gatewayRefundId, long amount, String currency,
                                 RefundStatus status, RefundSpeed speedRequested,
                                 String speedProcessed, String providerReference,
                                 String failureCode, String failureDescription,
                                 int providerAttemptCount, Instant nextRetryAt,
                                 Instant initiatedAt, Instant completedAt, Instant updatedAt) {}

    public enum ItemCondition { GOOD, DAMAGED, MISSING_TAGS, USED }
    public record InspectReturnItemRequest(@NotNull ItemCondition condition) {}
}
