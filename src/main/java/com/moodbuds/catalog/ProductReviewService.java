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

    public PageResponse<ReviewResponse> productReviews(long productId, int page, int size) {
        long total = jdbc.sql("SELECT COUNT(*) FROM product_reviews WHERE product_id=:productId")
                .param("productId", productId).query(Long.class).single();

        List<ReviewResponse> reviews = jdbc.sql("""
                SELECT pr.id, pr.product_id, u.id user_id, u.email user_email, pr.rating, pr.title, pr.comment,
                       pr.is_verified_purchase, pr.helpful_count, pr.created_at
                FROM product_reviews pr
                JOIN users u ON u.id=pr.user_id
                WHERE pr.product_id=:productId
                ORDER BY pr.created_at DESC
                LIMIT :limit OFFSET :offset
                """)
                .param("productId", productId)
                .param("limit", size)
                .param("offset", page * size)
                .query((rs, rowNum) -> reviewResponse(rs)).list();

        return PageResponse.of(reviews, page, size, total);
    }

    private ReviewResponse reviewResponse(ResultSet rs) throws SQLException {
        return new ReviewResponse(
                rs.getLong("id"),
                rs.getLong("user_id"),
                extractInitial(rs.getString("user_email")),
                rs.getInt("rating"),
                rs.getString("title"),
                rs.getString("comment"),
                rs.getBoolean("is_verified_purchase"),
                rs.getInt("helpful_count"),
                rs.getTimestamp("created_at").toInstant()
        );
    }

    private String extractInitial(String email) {
        return email == null || email.isEmpty() ? "?" : String.valueOf(email.charAt(0)).toUpperCase();
    }

    public record ReviewResponse(
            long id, long userId, String userInitial, int rating, String title, String comment,
            boolean verifiedPurchase, int helpfulCount, Instant createdAt) {}
}
