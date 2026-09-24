package com.moodbuds.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "moodbuds.razorpay")
public record RazorpayProperties(String mode, String keyId, String keySecret, String webhookSecret, String baseUrl,
                                 String checkoutName, String checkoutDescription,
                                 String themeColor) {
    public RazorpayProperties {
        mode = blank(mode) ? "DISABLED" : mode.trim().toUpperCase(java.util.Locale.ROOT);
        baseUrl = blank(baseUrl) ? "https://api.razorpay.com" : baseUrl;
        checkoutName = blank(checkoutName) ? "MoodBuds" : checkoutName;
        checkoutDescription = blank(checkoutDescription) ? "MoodBuds order" : checkoutDescription;
        themeColor = blank(themeColor) ? "#111827" : themeColor;
    }

    public boolean configured() {
        return mockMode() || (!blank(keyId) && !blank(keySecret));
    }

    public boolean mockMode() { return "MOCK".equals(mode); }
    public boolean razorpayMode() { return "RAZORPAY".equals(mode); }
    public boolean available() { return mockMode() || (razorpayMode() && configured()); }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
