package com.moodbuds.shipping;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "moodbuds.shipping")
public record ShippingProperties(String mode, String pricing, String baseUrl, String apiEmail,
                                 String apiPassword, String pickupLocation, String originPincode,
                                 String webhookToken, Long channelId, Duration quoteValidity,
                                 Long flatRate,
                                 Integer defaultWeightGrams, BigDecimal defaultLengthCm,
                                 BigDecimal defaultWidthCm, BigDecimal defaultHeightCm,
                                 String warehouseName, String warehouseEmail, String warehousePhone,
                                 String warehouseAddress, String warehouseCity, String warehouseState,
                                 String warehouseCountry) {
    public ShippingProperties {
        mode = blank(mode) ? "DISABLED" : mode.trim().toUpperCase(Locale.ROOT);
        pricing = blank(pricing) ? "FREE" : pricing.trim().toUpperCase(Locale.ROOT);
        baseUrl = blank(baseUrl) ? "https://apiv2.shiprocket.in" : baseUrl.trim();
        pickupLocation = blank(pickupLocation) ? "Primary" : pickupLocation.trim();
        originPincode = blank(originPincode) ? "560001" : originPincode.trim();
        quoteValidity = quoteValidity == null ? Duration.ofMinutes(15) : quoteValidity;
        flatRate = flatRate == null ? 0 : Math.max(flatRate, 0);
        defaultWeightGrams = defaultWeightGrams == null ? 500 : defaultWeightGrams;
        defaultLengthCm = defaultLengthCm == null ? new BigDecimal("25") : defaultLengthCm;
        defaultWidthCm = defaultWidthCm == null ? new BigDecimal("20") : defaultWidthCm;
        defaultHeightCm = defaultHeightCm == null ? new BigDecimal("5") : defaultHeightCm;
        warehouseName = blank(warehouseName) ? "MoodBuds Returns" : warehouseName;
        warehouseEmail = blank(warehouseEmail) ? "returns@moodbuds.local" : warehouseEmail;
        warehousePhone = blank(warehousePhone) ? "9999999999" : warehousePhone;
        warehouseAddress = blank(warehouseAddress) ? "MoodBuds Warehouse" : warehouseAddress;
        warehouseCity = blank(warehouseCity) ? "Bengaluru" : warehouseCity;
        warehouseState = blank(warehouseState) ? "Karnataka" : warehouseState;
        warehouseCountry = blank(warehouseCountry) ? "India" : warehouseCountry;
    }

    public boolean mockMode() { return "MOCK".equals(mode); }
    public boolean shiprocketMode() { return "SHIPROCKET".equals(mode); }
    public boolean available() { return mockMode() || (shiprocketMode() && configured()); }
    public boolean configured() { return !blank(apiEmail) && !blank(apiPassword) && !blank(originPincode); }
    public boolean providerRatePricing() { return "PROVIDER_RATE".equals(pricing); }
    public boolean flatPricing() { return "FLAT".equals(pricing); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
