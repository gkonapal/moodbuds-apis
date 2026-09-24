package com.moodbuds.shipping;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

public interface ShippingGateway {
    Quote quote(String pickupPincode, String deliveryPincode, int weightGrams,
                BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm, boolean reverse);
    Booking bookForward(ForwardOrder order, Quote quote);
    Booking bookReverse(ReverseOrder order, Quote quote);
    Tracking track(String awbCode);
    boolean mockMode();
    String providerName();

    record Quote(boolean serviceable, String courierCompanyId, String courierName, long charge,
                 LocalDate estimatedDeliveryDate, String message, JsonNode raw) {}
    record LineItem(String name, String sku, int units, long sellingPrice) {}
    record Contact(String firstName, String lastName, String email, String phone, String address,
                   String address2, String city, String state, String country, String pincode) {}
    record Parcel(int weightGrams, BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm) {}
    record ForwardOrder(long moodbudsOrderId, String orderNumber, LocalDate orderDate, Contact customer,
                        List<LineItem> items, long subtotal, Parcel parcel) {}
    record ReverseOrder(long moodbudsOrderId, long returnRequestId, String returnNumber, LocalDate orderDate,
                        Contact pickup, Contact warehouse, List<LineItem> items, long subtotal, Parcel parcel) {}
    record Booking(String providerOrderId, String providerShipmentId, String courierCompanyId,
                   String courierName, String awbCode, String status, String pickupStatus,
                   String trackingUrl, LocalDate estimatedPickupDate,
                   LocalDate estimatedDeliveryDate, long charge, JsonNode raw) {}
    record Tracking(String providerStatus, String normalizedStatus, String message, String location,
                    LocalDate estimatedDeliveryDate, JsonNode raw) {}
}
