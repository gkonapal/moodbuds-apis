package com.moodbuds.catalog.api;

import java.util.List;

public final class CatalogDtos {
    private CatalogDtos() {}

    public record CategorySummary(long id, String name, String slug, long subcategoryCount, long productCount) {}

    public record SubcategorySummary(long id, String name, String slug, long categoryId,
                                     String categoryName, String categorySlug, long productCount) {}

    public record CategoryDetail(long id, String name, String slug, long productCount,
                                 List<SubcategorySummary> subcategories) {}

    public record MoodSummary(long id, String name, String slug, String emoji, String tagline, String personalityTagline,
                              String bannerImageUrl, String color, int displayOrder, long productCount) {}

    public record ProductCard(long id, String sku, String slug, String name,
                              long price, Long discountPrice, long effectivePrice, Integer discountPercent,
                              String primaryImageUrl, double averageRating, long reviewCount, boolean inStock,
                              boolean featured, boolean newArrival, boolean bestSeller,
                              CategoryRef category, SubcategoryRef subcategory) {}

    public record CategoryRef(long id, String name, String slug) {}
    public record SubcategoryRef(long id, String name, String slug) {}
    public record GstRate(String name, String hsnCode, String ratePercentage) {}
    public record ProductImage(long id, String imageUrl, boolean primary) {}
    public record ProductSize(String size, boolean inStock) {}
    public record MoodRef(long id, String name, String slug, String color) {}
    public record SizeChart(String source, String chartImageUrl) {}

    public record ProductDetail(long id, String sku, String slug, String name, String description,
                                String fabricDetails, String colorName,
                                long price, Long discountPrice, long effectivePrice, Integer discountPercent,
                                double averageRating, long reviewCount,
                                boolean inStock, boolean featured, boolean newArrival, boolean bestSeller,
                                int returnWindowDays, CategoryRef category, SubcategoryRef subcategory,
                                GstRate gst, List<ProductImage> images, List<ProductSize> sizes,
                                List<MoodRef> moods, SizeChart sizeChart) {}

    public record ProductAvailability(String slug, boolean inStock, List<ProductSize> sizes) {}

    public record HomeResponse(List<MoodSummary> moods, List<CategorySummary> categories,
                               List<ProductCard> featured, List<ProductCard> newArrivals,
                               List<ProductCard> bestSellers) {}
}
