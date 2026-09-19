package com.moodbuds.admin;

public final class ProductSummaryDtos {
    private ProductSummaryDtos() {}

    public record ProductSummary(
            long id,
            String sku,
            String name,
            long price,
            Long discountPrice,
            long stock,
            int lowStockThreshold,
            Long primaryMediaId,
            String primaryImageUrl,
            String moodName,
            String moodColor,
            String publicationStatus) {}
}
