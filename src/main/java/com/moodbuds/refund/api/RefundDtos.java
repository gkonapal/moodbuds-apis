package com.moodbuds.refund.api;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

public final class RefundDtos {
    private RefundDtos() {}

    public enum RefundStatus { PENDING, INITIATED, PROCESSED, FAILED }
    public enum RefundSpeed { NORMAL, OPTIMUM }
    public enum MockRefundOutcome { SUCCESS, FAILED }

    public record InitiateRefundRequest(@NotNull RefundSpeed speed) {
        public InitiateRefundRequest { speed = speed == null ? RefundSpeed.OPTIMUM : speed; }
    }

    public record MockRefundRequest(@NotNull MockRefundOutcome outcome) {}

    public record RefundResponse(long id, String orderNumber, long returnRequestId,
                                 String customerName, String customerEmail,
                                 JsonNode productSnapshot, int itemCount, int totalQuantity,
                                 String gatewayRefundId, long amount, String currency,
                                 RefundStatus status, RefundSpeed speedRequested,
                                 String speedProcessed, String providerReference,
                                 String failureCode, String failureDescription,
                                 int providerAttemptCount, Instant nextRetryAt,
                                 Instant initiatedAt, Instant completedAt, Instant updatedAt) {
        public RefundResponse(long id, String orderNumber, long returnRequestId,
                              String gatewayRefundId, long amount, String currency,
                              RefundStatus status, RefundSpeed speedRequested,
                              String speedProcessed, String providerReference,
                              String failureCode, String failureDescription,
                              int providerAttemptCount, Instant nextRetryAt,
                              Instant initiatedAt, Instant completedAt, Instant updatedAt) {
            this(id, orderNumber, returnRequestId, null, null, null, 0, 0,
                    gatewayRefundId, amount, currency, status, speedRequested, speedProcessed,
                    providerReference, failureCode, failureDescription, providerAttemptCount,
                    nextRetryAt, initiatedAt, completedAt, updatedAt);
        }
    }

    public enum ItemCondition { GOOD, DAMAGED, MISSING_TAGS, USED }
    public record InspectReturnItemRequest(@NotNull ItemCondition condition) {}
}
