package com.moodbuds.admin;

import static com.moodbuds.admin.ProductSummaryDtos.ProductSummary;

import java.sql.ResultSet;
import java.sql.SQLException;

import com.moodbuds.common.PageResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductSummaryService {
    private static final String SELECT = """
            SELECT p.id,p.sku,p.name,p.price,p.discount_price,p.publication_status,
                   (SELECT COALESCE(SUM(ps.stock_quantity),0) FROM product_sizes ps
                    WHERE ps.product_id=p.id) stock,
                   (SELECT COALESCE(MIN(ps.low_stock_threshold),0) FROM product_sizes ps
                    WHERE ps.product_id=p.id AND ps.is_available=1) low_stock_threshold,
                   (SELECT pi.media_asset_id FROM product_images pi WHERE pi.product_id=p.id
                    ORDER BY pi.is_primary DESC,pi.sort_order,pi.id LIMIT 1) primary_media_id,
                   (SELECT pi.image_url FROM product_images pi WHERE pi.product_id=p.id
                    ORDER BY pi.is_primary DESC,pi.sort_order,pi.id LIMIT 1) primary_image_url,
                   (SELECT m.name FROM product_mood_tags pmt JOIN moods m ON m.id=pmt.mood_id
                    WHERE pmt.product_id=p.id ORDER BY m.id LIMIT 1) mood_name,
                   (SELECT m.color FROM product_mood_tags pmt JOIN moods m ON m.id=pmt.mood_id
                    WHERE pmt.product_id=p.id ORDER BY m.id LIMIT 1) mood_color
            FROM products p
            """;

    private final JdbcClient jdbc;

    public ProductSummaryService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductSummary> list(int page, int size, String query) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String where = query == null || query.isBlank() ? "" : " WHERE p.name LIKE :query OR p.sku LIKE :query";
        var rows = jdbc.sql(SELECT + where + " ORDER BY p.id DESC LIMIT :limit OFFSET :offset")
                .param("limit", safeSize)
                .param("offset", safePage * safeSize);
        var count = jdbc.sql("SELECT COUNT(*) FROM products p" + where);
        if (!where.isEmpty()) {
            String pattern = "%" + query.trim() + "%";
            rows = rows.param("query", pattern);
            count = count.param("query", pattern);
        }
        return PageResponse.of(rows.query(this::map).list(), safePage, safeSize,
                count.query(Long.class).single());
    }

    private ProductSummary map(ResultSet rs, int rowNum) throws SQLException {
        return new ProductSummary(
                rs.getLong("id"), rs.getString("sku"), rs.getString("name"), rs.getLong("price"),
                nullableLong(rs, "discount_price"), rs.getLong("stock"), rs.getInt("low_stock_threshold"),
                nullableLong(rs, "primary_media_id"), rs.getString("primary_image_url"), rs.getString("mood_name"), rs.getString("mood_color"),
                rs.getString("publication_status"));
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
