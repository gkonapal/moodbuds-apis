package com.moodbuds.admin;

import java.util.List;

final class InventoryOverviewDtos {
    private InventoryOverviewDtos() {}

    record Overview(Summary summary, List<Product> products) {}

    record Summary(
            long totalProducts,
            long totalSizeVariants,
            long totalUnits,
            long inStockProducts,
            long lowStockVariants,
            long outOfStockVariants,
            long stockValue) {}

    record Product(
            long productId,
            String sku,
            String name,
            String imageUrl,
            List<String> moods,
            int totalStock,
            long stockValue,
            String status,
            List<Size> sizes) {}

    record Size(
            long sizeId,
            String size,
            int stockQuantity,
            int lowStockThreshold,
            boolean available,
            String status) {}
}
