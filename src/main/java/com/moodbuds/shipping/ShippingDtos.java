package com.moodbuds.shipping;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ShippingDtos {
    private ShippingDtos() {}

    public record ShippingQuoteResponse(long quoteId, boolean serviceable, String providerMode,
                                        String courierCompanyId, String courierName, String message,
                                        String deliveryPincode, int weightGrams, long providerCharge,
                                        long customerShippingCost, LocalDate estimatedDeliveryDate,
                                        Instant quoteExpiresAt) {}

    public record ShipmentResponse(long id, String orderNumber, Long returnRequestId, String direction,
                                   String provider, String providerOrderId, String providerShipmentId,
                                   String courierName, String awbCode, String status, String pickupStatus,
                                   String trackingUrl, long shippingCharge, LocalDate estimatedPickupDate,
                                   LocalDate estimatedDeliveryDate, String lastLocation,
                                   String failureDescription, List<ShipmentEventResponse> events,
                                   Instant createdAt, Instant updatedAt) {}

    public record ShipmentEventResponse(String providerStatus, String status, String message,
                                        String location, Instant eventAt) {}

    public record MockShipmentUpdateRequest(String status, String message, String location,
                                            LocalDate estimatedPickupDate, LocalDate estimatedDeliveryDate) {}
}
