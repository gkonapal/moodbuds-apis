package com.moodbuds.catalog;

public final class CatalogPricing {
    private CatalogPricing() {}

    public static long effectivePrice(long price, Long discountPrice) {
        return discountPrice != null && discountPrice < price ? discountPrice : price;
    }

    public static Integer discountPercent(long price, Long discountPrice) {
        if (price <= 0 || discountPrice == null || discountPrice >= price) return null;
        return (int) ((price - discountPrice) * 100 / price);
    }
}
