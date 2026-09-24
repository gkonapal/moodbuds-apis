package com.moodbuds.order.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.moodbuds.shipping.ShippingDtos.ShipmentResponse;

public final class OrderDtos {
    private OrderDtos() {}

    public enum PaymentMethod { UPI, CREDIT_CARD, DEBIT_CARD, NET_BANKING, WALLET, COD }
    public enum OrderStatus {
        PENDING_PAYMENT, PAYMENT_FAILED, PROCESSING, CONFIRMED, PACKED, SHIPPED,
        OUT_FOR_DELIVERY, DELIVERED, CANCELLED, RETURN_INITIATED,
        RETURN_PICKUP_SCHEDULED, RETURN_RECEIVED, REFUND_INITIATED, PARTIALLY_REFUNDED, REFUNDED
    }

    public record PlaceOrderRequest(@Positive long addressId, PaymentMethod paymentMethod) {}
    public record CancelOrderRequest(@NotBlank @Size(max = 255) String reason) {}
    public record CancellationEligibilityResponse(String orderNumber, OrderStatus status, boolean canCancel,
                                                  String reasonCode, String message) {}

    public record OrderSummaryResponse(String orderNumber, OrderStatus status, PaymentMethod paymentMethod,
                                       int itemCount, int totalQuantity, String primaryImageUrl,
                                       long totalAmount, boolean paymentRequired, LocalDate expectedDeliveryDate,
                                       String shippingStatus, String courierName, Instant createdAt,
                                       Instant updatedAt, Instant paymentExpiresAt) {}

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
                                      String shippingPricingStatus, String shippingPricingSource,
                                      LocalDate expectedDeliveryDate, ShipmentResponse shipment,
                                      List<OrderItemResponse> items, List<OrderStatusEvent> statusHistory,
                                      List<PaymentSummary> payments, Instant createdAt, Instant updatedAt,
                                      Instant paymentExpiresAt, Instant cancelledAt, String cancellationReason,
                                      boolean canCancel) {}
}
