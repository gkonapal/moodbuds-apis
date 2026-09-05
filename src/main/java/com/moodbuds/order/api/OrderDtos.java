package com.moodbuds.order.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public final class OrderDtos {
    private OrderDtos() {}

    public enum PaymentMethod { UPI, CREDIT_CARD, DEBIT_CARD, NET_BANKING, WALLET, COD }
    public enum OrderStatus {
        PENDING_PAYMENT, PAYMENT_FAILED, PROCESSING, CONFIRMED, PACKED, SHIPPED,
        OUT_FOR_DELIVERY, DELIVERED, CANCELLED, RETURN_INITIATED,
        RETURN_PICKUP_SCHEDULED, RETURN_RECEIVED, REFUND_INITIATED, REFUNDED
    }

    public record PlaceOrderRequest(@Positive long addressId, @NotNull PaymentMethod paymentMethod) {}

    public record OrderSummaryResponse(String orderNumber, OrderStatus status, PaymentMethod paymentMethod,
                                       int itemCount, int totalQuantity, String primaryImageUrl,
                                       long totalAmount, boolean paymentRequired, Instant createdAt,
                                       Instant updatedAt) {}

    public record OrderItemResponse(long id, long productId, JsonNode productSnapshot, String size,
                                    int quantity, long unitPrice, Long unitDiscountPrice,
                                    BigDecimal gstRatePercentage, long gstAmount, long lineTotal) {}

    public record OrderStatusEvent(String fromStatus, String toStatus, String notes, Instant createdAt) {}

    public record PaymentSummary(String gateway, String method, String status, long amount,
                                 String currency, Instant initiatedAt, Instant completedAt) {}

    public record OrderDetailResponse(String orderNumber, OrderStatus status,
                                      PaymentMethod paymentMethod, JsonNode shippingAddress,
                                      String couponCode, long subtotal, long couponDiscount,
                                      long shippingCost, long gstAmount, long totalAmount,
                                      boolean paymentRequired, boolean shippingProviderPending,
                                      List<OrderItemResponse> items, List<OrderStatusEvent> statusHistory,
                                      List<PaymentSummary> payments, Instant createdAt, Instant updatedAt) {}
}
