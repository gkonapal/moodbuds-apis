package com.moodbuds.admin;

import static com.moodbuds.admin.InventoryOverviewDtos.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class InventoryOverviewService {
    private static final String PRODUCT_SQL = """
            SELECT p.id,p.sku,p.name,COALESCE(p.discount_price,p.price) unit_price,
                   (SELECT pi.image_url FROM product_images pi WHERE pi.product_id=p.id
                    ORDER BY pi.is_primary DESC,pi.id LIMIT 1) image_url,
                   (SELECT GROUP_CONCAT(DISTINCT m.name ORDER BY m.name SEPARATOR '|||')
                    FROM product_mood_tags pmt JOIN moods m ON m.id=pmt.mood_id
                    WHERE pmt.product_id=p.id) mood_names
            FROM products p
            ORDER BY p.name,p.id
            """;

    private static final String SIZE_SQL = """
            SELECT ps.id,ps.product_id,ps.size,ps.stock_quantity,ps.low_stock_threshold,ps.is_available
            FROM product_sizes ps
            ORDER BY ps.product_id,ps.id
            """;

    private final JdbcClient jdbc;

    InventoryOverviewService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    Overview overview() {
        var products = jdbc.sql(PRODUCT_SQL).query((rs, rowNum) -> new ProductRow(
                rs.getLong("id"),
                rs.getString("sku"),
                rs.getString("name"),
                rs.getString("image_url"),
                splitMoods(rs.getString("mood_names")),
                rs.getLong("unit_price"))).list();
        var sizes = jdbc.sql(SIZE_SQL).query((rs, rowNum) -> new SizeRow(
                rs.getLong("id"),
                rs.getLong("product_id"),
                rs.getString("size"),
                rs.getInt("stock_quantity"),
                rs.getInt("low_stock_threshold"),
                rs.getBoolean("is_available"))).list();
        return assemble(products, sizes);
    }

    static Overview assemble(List<ProductRow> productRows, List<SizeRow> sizeRows) {
        Map<Long, List<SizeRow>> sizesByProduct = new LinkedHashMap<>();
        for (var size : sizeRows) {
            sizesByProduct.computeIfAbsent(size.productId(), ignored -> new ArrayList<>()).add(size);
        }

        List<Product> products = new ArrayList<>();
        long totalUnits = 0;
        long totalValue = 0;
        long inStockProducts = 0;
        long lowStockVariants = 0;
        long outOfStockVariants = 0;

        for (var product : productRows) {
            List<Size> sizes = new ArrayList<>();
            boolean hasSellableSize = false;
            boolean hasLowStockSize = false;
            int productStock = 0;

            for (var row : sizesByProduct.getOrDefault(product.id(), List.of())) {
                String status = sizeStatus(row);
                sizes.add(new Size(row.id(), row.size(), row.stockQuantity(), row.lowStockThreshold(), row.available(), status));
                productStock += row.stockQuantity();
                hasSellableSize |= row.available() && row.stockQuantity() > 0;
                hasLowStockSize |= "LOW_STOCK".equals(status);
                if ("LOW_STOCK".equals(status)) lowStockVariants++;
                if ("OUT_OF_STOCK".equals(status)) outOfStockVariants++;
            }

            String productStatus = !hasSellableSize ? "OUT_OF_STOCK" : hasLowStockSize ? "LOW_STOCK" : "IN_STOCK";
            if (hasSellableSize) inStockProducts++;
            long productValue = product.unitPrice() * productStock;
            totalUnits += productStock;
            totalValue += productValue;
            products.add(new Product(product.id(), product.sku(), product.name(), product.imageUrl(), product.moods(),
                    productStock, productValue, productStatus, List.copyOf(sizes)));
        }

        var summary = new Summary(productRows.size(), sizeRows.size(), totalUnits, inStockProducts,
                lowStockVariants, outOfStockVariants, totalValue);
        return new Overview(summary, List.copyOf(products));
    }

    private static String sizeStatus(SizeRow size) {
        if (size.stockQuantity() == 0) return "OUT_OF_STOCK";
        if (!size.available()) return "DISABLED";
        if (size.stockQuantity() <= size.lowStockThreshold()) return "LOW_STOCK";
        return "IN_STOCK";
    }

    private static List<String> splitMoods(String moods) {
        if (moods == null || moods.isBlank()) return List.of();
        return Arrays.stream(moods.split("\\|\\|\\|"))
                .filter(value -> !value.isBlank())
                .toList();
    }

    record ProductRow(long id, String sku, String name, String imageUrl, List<String> moods, long unitPrice) {}
    record SizeRow(long id, long productId, String size, int stockQuantity, int lowStockThreshold, boolean available) {}
}
