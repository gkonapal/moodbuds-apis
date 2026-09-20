package com.moodbuds.catalog;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import com.moodbuds.common.ApiException;
import com.moodbuds.common.PageResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class ProductReviewService {
    private final JdbcClient jdbc;

    public ProductReviewService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public PageResponse<ReviewResponse> productReviews(String productSlug, int page, int size) {
        long productId = jdbc.sql("""
                SELECT p.id FROM products p
                JOIN categories c ON c.id=p.category_id AND c.is_active=1
                JOIN subcategories sc ON sc.id=p.subcategory_id AND sc.is_active=1
                WHERE p.slug=:slug AND p.is_active=1 AND p.publication_status='PUBLISHED'
                """).param("slug", productSlug).query(Long.class).optional()
                .orElseThrow(() -> ApiException.notFound("Product"));

        long total = jdbc.sql("SELECT COUNT(*) FROM product_reviews WHERE product_id=:productId")
                .param("productId", productId).query(Long.class).single();
        List<ReviewResponse> content = jdbc.sql("""
                SELECT pr.id,u.email user_email,pr.rating,pr.title,pr.comment,
                       pr.is_verified_purchase,pr.helpful_count,pr.created_at
                FROM product_reviews pr
                JOIN users u ON u.id=pr.user_id AND u.is_active=1
                WHERE pr.product_id=:productId
                ORDER BY pr.created_at DESC,pr.id DESC
                LIMIT :limit OFFSET :offset
                """).param("productId", productId).param("limit", size).param("offset", page * size)
                .query((rs, row) -> review(rs)).list();
        return PageResponse.of(content, page, size, total);
    }

    private static ReviewResponse review(ResultSet rs) throws SQLException {
        String email = rs.getString("user_email");
        String initial = email == null || email.isBlank() ? "?" : email.substring(0, 1).toUpperCase();
        return new ReviewResponse(rs.getLong("id"), initial,
                rs.getInt("rating"), rs.getString("title"), rs.getString("comment"),
                rs.getBoolean("is_verified_purchase"), rs.getInt("helpful_count"),
                rs.getTimestamp("created_at").toInstant());
    }

    public record ReviewResponse(long id, String userInitial, int rating,
                                 String title, String comment, boolean verifiedPurchase,
                                 int helpfulCount, Instant createdAt) {}
}
