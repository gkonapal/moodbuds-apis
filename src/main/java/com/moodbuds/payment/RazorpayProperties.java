package com.moodbuds.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "moodbuds.razorpay")
public record RazorpayProperties(String keyId, String keySecret, String baseUrl,
                                 String checkoutName, String checkoutDescription,
                                 String themeColor) {
    public RazorpayProperties {
        baseUrl = blank(baseUrl) ? "https://api.razorpay.com" : baseUrl;
        checkoutName = blank(checkoutName) ? "MoodBuds" : checkoutName;
        checkoutDescription = blank(checkoutDescription) ? "MoodBuds order" : checkoutDescription;
        themeColor = blank(themeColor) ? "#111827" : themeColor;
    }

    public boolean configured() {
        return !blank(keyId) && !blank(keySecret);
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
