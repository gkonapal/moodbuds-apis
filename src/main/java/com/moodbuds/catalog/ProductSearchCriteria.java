package com.moodbuds.catalog;

import com.moodbuds.common.ApiException;

public record ProductSearchCriteria(
        String query, String category, String subcategory, String mood, String productSize, String color,
        Long minPrice, Long maxPrice, Boolean inStock, Boolean featured, Boolean newArrival,
        Boolean bestSeller, String sort, int page, int pageSize) {

    public ProductSearchCriteria {
        query = clean(query);
        category = clean(category);
        subcategory = clean(subcategory);
        mood = clean(mood);
        productSize = clean(productSize);
        color = clean(color);
        sort = clean(sort) == null ? "newest" : clean(sort).toLowerCase(java.util.Locale.ROOT);
        if (minPrice != null && minPrice < 0 || maxPrice != null && maxPrice < 0) {
            throw ApiException.badRequest("INVALID_PRICE_RANGE", "Prices cannot be negative");
        }
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            throw ApiException.badRequest("INVALID_PRICE_RANGE", "minPrice cannot be greater than maxPrice");
        }
        if (page < 0 || pageSize < 1 || pageSize > 100) {
            throw ApiException.badRequest("INVALID_PAGINATION", "page must be at least 0 and size must be between 1 and 100");
        }
        if (!java.util.Set.of("newest", "price-asc", "price-desc", "name-asc", "featured").contains(sort)) {
            throw ApiException.badRequest("INVALID_SORT", "sort must be one of newest, price-asc, price-desc, name-asc, featured");
        }
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
