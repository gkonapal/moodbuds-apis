package com.moodbuds.returns.api;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import com.moodbuds.shipping.ShippingDtos.ShipmentResponse;

public final class ReturnDtos {
    private ReturnDtos() {}

    public enum ReturnStatus {
        REQUESTED, APPROVED, REJECTED, PICKUP_SCHEDULED, ITEM_RECEIVED,
        QUALITY_CHECK_PASSED, QUALITY_CHECK_FAILED, REFUND_INITIATED, COMPLETED, CLOSED
    }
    public enum ReturnReason {
        DEFECTIVE_PRODUCT, WRONG_ITEM_RECEIVED, SIZE_DOES_NOT_FIT, CHANGED_MIND, OTHER
    }
    public enum ReturnItemReason { DEFECTIVE, WRONG_SIZE, WRONG_ITEM, OTHER }

    public record CreateReturnRequest(
            @NotNull ReturnReason reason,
            @Size(max = 1000) String reasonDescription,
            @Positive long pickupAddressId,
            @Size(max = 5) List<@NotBlank @Size(max = 2048) String> images,
            @NotEmpty @Size(max = 50) List<@Valid ReturnItemRequest> items) {}

    public record ReturnItemRequest(@Positive long orderItemId,
                                    @Min(1) @Max(32767) int quantity,
                                    @NotNull ReturnItemReason reason) {}

    public record ReturnEligibilityResponse(String orderNumber, String orderStatus, Instant deliveredAt,
                                            boolean canCreateReturn, String reasonCode,
                                            List<ReturnItemEligibility> items) {}

    public record ReturnItemEligibility(long orderItemId, JsonNode productSnapshot, String size,
                                        int purchasedQuantity, int previouslyRequestedQuantity,
                                        int availableQuantity, int returnWindowDays, Instant eligibleUntil,
                                        boolean eligible, String reasonCode) {}

    public record ReturnSummaryResponse(long id, String orderNumber, ReturnStatus status,
                                        ReturnReason reason, int itemCount, int totalQuantity,
                                        Instant requestedAt, Instant updatedAt) {}

    public record ReturnItemResponse(long id, long orderItemId, JsonNode productSnapshot, String size,
                                     int quantity, ReturnItemReason reason, String conditionOnReceipt) {}

    public record ReturnHistoryResponse(long id, String fromStatus, String toStatus,
                                        String notes, String reason, String actorType,
                                        Instant createdAt) {}

    public record ReturnRefundResponse(long id, long amount, String currency, String status,
                                       String providerReference, String failureDescription,
                                       Instant initiatedAt, Instant completedAt, Instant updatedAt) {}

    public record ReturnDetailResponse(long id, String orderNumber, ReturnStatus status,
                                       ReturnReason reason, String reasonDescription,
                                       long pickupAddressId, JsonNode pickupAddress,
                                       JsonNode images, String adminNotes, String rejectionReason,
                                       List<ReturnItemResponse> items,
                                       List<ReturnHistoryResponse> history,
                                       ReturnRefundResponse refund,
                                       ShipmentResponse reverseShipment,
                                       Instant requestedAt, Instant updatedAt) {}
}
