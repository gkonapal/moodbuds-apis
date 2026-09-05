package com.moodbuds.wishlist;

import static com.moodbuds.wishlist.api.WishlistDtos.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

import com.moodbuds.catalog.CatalogPricing;
import com.moodbuds.common.ApiException;
import com.moodbuds.customer.CustomerProfileService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WishlistService {
    private final JdbcClient jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final CustomerProfileService customers;

    public WishlistService(JdbcClient jdbc, NamedParameterJdbcTemplate namedJdbc, CustomerProfileService customers) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
        this.customers = customers;
    }

    public WishlistResponse get(long customerId) {
        customers.requireActive(customerId);
        Long wishlistId = jdbc.sql("SELECT id FROM wishlists WHERE user_id=:userId")
                .param("userId", customerId).query(Long.class).optional().orElse(null);
        if (wishlistId == null) return new WishlistResponse(null, 0, List.of());
        var items = jdbc.sql("""
                SELECT wi.id,wi.product_id,wi.size,wi.added_at,p.sku,p.slug,p.name,p.price,p.discount_price,
                       (SELECT pi.image_url FROM product_images pi WHERE pi.product_id=p.id
                        ORDER BY pi.is_primary DESC,pi.id LIMIT 1) primary_image_url,
                       (p.is_active=1 AND c.is_active=1 AND sc.is_active=1) product_available,
                       CASE WHEN wi.size IS NULL
                            THEN EXISTS(SELECT 1 FROM product_sizes ps WHERE ps.product_id=p.id
                                        AND ps.is_available=1 AND ps.stock_quantity>0)
                            ELSE EXISTS(SELECT 1 FROM product_sizes ps WHERE ps.product_id=p.id AND ps.size=wi.size
                                        AND ps.is_available=1 AND ps.stock_quantity>0) END selected_size_available
                FROM wishlist_items wi
                JOIN products p ON p.id=wi.product_id
                JOIN categories c ON c.id=p.category_id
                JOIN subcategories sc ON sc.id=p.subcategory_id
                WHERE wi.wishlist_id=:wishlistId
                ORDER BY wi.added_at DESC,wi.id DESC
                """).param("wishlistId", wishlistId).query((rs, rowNum) -> item(rs)).list();
        return new WishlistResponse(wishlistId, items.size(), items);
    }

    @Transactional
    public WishlistItemResponse add(long customerId, AddWishlistItemRequest request) {
        customers.requireActive(customerId);
        String slug = request.productSlug().trim();
        String size = clean(request.size());
        var product = activeProduct(slug);
        if (size != null) validateConfiguredSize(product.id(), size);
        long wishlistId = ensureWishlist(customerId);
        var existing = findItem(wishlistId, product.id(), size, true);
        if (existing != null) return itemById(customerId, existing);
        var keys = new GeneratedKeyHolder();
        namedJdbc.update("""
                INSERT INTO wishlist_items(wishlist_id,product_id,size,added_at)
                VALUES(:wishlistId,:productId,:size,UTC_TIMESTAMP())
                """, new MapSqlParameterSource().addValue("wishlistId", wishlistId)
                .addValue("productId", product.id()).addValue("size", size, Types.VARCHAR),
                keys, new String[]{"id"});
        return itemById(customerId, keys.getKey().longValue());
    }

    public WishlistStatusResponse status(long customerId, String productSlug, String requestedSize) {
        customers.requireActive(customerId);
        String slug = productSlug.trim();
        String size = clean(requestedSize);
        var row = jdbc.sql("""
                SELECT wi.id FROM wishlists w
                JOIN wishlist_items wi ON wi.wishlist_id=w.id
                JOIN products p ON p.id=wi.product_id
                WHERE w.user_id=:userId AND p.slug=:slug AND wi.size <=> :size
                """).param("userId", customerId).param("slug", slug).param("size", size, Types.VARCHAR)
                .query(Long.class).optional();
        return new WishlistStatusResponse(slug, size, row.isPresent(), row.orElse(null));
    }

    @Transactional
    public void remove(long customerId, long itemId) {
        customers.requireActive(customerId);
        int changed = jdbc.sql("""
                DELETE wi FROM wishlist_items wi JOIN wishlists w ON w.id=wi.wishlist_id
                WHERE wi.id=:itemId AND w.user_id=:userId
                """).param("itemId", itemId).param("userId", customerId).update();
        if (changed == 0) throw ApiException.notFound("Wishlist item");
    }

    @Transactional
    public void clear(long customerId) {
        customers.requireActive(customerId);
        jdbc.sql("""
                DELETE wi FROM wishlist_items wi JOIN wishlists w ON w.id=wi.wishlist_id
                WHERE w.user_id=:userId
                """).param("userId", customerId).update();
    }

    @Transactional
    public MoveToCartResponse moveToCart(long customerId, long itemId, MoveToCartRequest request) {
        customers.requireActive(customerId);
        jdbc.sql("SELECT id FROM users WHERE id=:id FOR UPDATE").param("id", customerId).query(Long.class).single();
        var item = jdbc.sql("""
                SELECT wi.id,wi.size,p.id product_id,p.slug,p.price,p.discount_price,
                       p.is_active,c.is_active category_active,sc.is_active subcategory_active
                FROM wishlist_items wi
                JOIN wishlists w ON w.id=wi.wishlist_id
                JOIN products p ON p.id=wi.product_id
                JOIN categories c ON c.id=p.category_id
                JOIN subcategories sc ON sc.id=p.subcategory_id
                WHERE wi.id=:itemId AND w.user_id=:userId FOR UPDATE
                """).param("itemId", itemId).param("userId", customerId)
                .query((rs, rowNum) -> new MoveItem(rs.getLong("id"), rs.getString("size"),
                        rs.getLong("product_id"), rs.getString("slug"), rs.getLong("price"),
                        nullableLong(rs, "discount_price"), rs.getBoolean("is_active"),
                        rs.getBoolean("category_active"), rs.getBoolean("subcategory_active")))
                .optional().orElseThrow(() -> ApiException.notFound("Wishlist item"));
        if (!item.productActive() || !item.categoryActive() || !item.subcategoryActive()) {
            throw new ApiException(HttpStatus.CONFLICT, "PRODUCT_UNAVAILABLE", "This product is not currently available");
        }
        String size = clean(request.size()) == null ? item.size() : clean(request.size());
        if (size == null) throw ApiException.badRequest("SIZE_REQUIRED", "Select a size before moving this item to the cart");
        var stock = jdbc.sql("""
                SELECT stock_quantity,is_available FROM product_sizes
                WHERE product_id=:productId AND size=:size FOR UPDATE
                """).param("productId", item.productId()).param("size", size)
                .query((rs, rowNum) -> new Stock(rs.getInt("stock_quantity"), rs.getBoolean("is_available")))
                .optional().orElseThrow(() -> ApiException.badRequest("INVALID_SIZE", "The selected size is not configured for this product"));
        long cartId = ensureCart(customerId);
        var existing = jdbc.sql("""
                SELECT id,quantity FROM cart_items WHERE cart_id=:cartId AND product_id=:productId AND size=:size FOR UPDATE
                """).param("cartId", cartId).param("productId", item.productId()).param("size", size)
                .query((rs, rowNum) -> new ExistingCartItem(rs.getLong("id"), rs.getInt("quantity"))).optional().orElse(null);
        int finalQuantity = request.quantity() + (existing == null ? 0 : existing.quantity());
        if (finalQuantity > 10) {
            throw ApiException.badRequest("CART_QUANTITY_LIMIT", "A cart item cannot exceed a quantity of 10");
        }
        if (!stock.available() || stock.quantity() < finalQuantity) {
            throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", "The requested quantity is not available");
        }
        Long validDiscount = item.discountPrice() != null && item.discountPrice() < item.price() ? item.discountPrice() : null;
        long cartItemId;
        if (existing == null) {
            var keys = new GeneratedKeyHolder();
            namedJdbc.update("""
                    INSERT INTO cart_items(cart_id,product_id,size,quantity,unit_price,unit_discount_price)
                    VALUES(:cartId,:productId,:size,:quantity,:price,:discount)
                    """, new MapSqlParameterSource().addValue("cartId", cartId).addValue("productId", item.productId())
                    .addValue("size", size).addValue("quantity", finalQuantity).addValue("price", item.price())
                    .addValue("discount", validDiscount, Types.INTEGER), keys, new String[]{"id"});
            cartItemId = keys.getKey().longValue();
        } else {
            cartItemId = existing.id();
            jdbc.sql("""
                    UPDATE cart_items SET quantity=:quantity,unit_price=:price,unit_discount_price=:discount
                    WHERE id=:id
                    """).param("quantity", finalQuantity).param("price", item.price())
                    .param("discount", validDiscount, Types.INTEGER).param("id", cartItemId).update();
        }
        jdbc.sql("DELETE FROM wishlist_items WHERE id=:id").param("id", item.id()).update();
        jdbc.sql("""
                UPDATE carts SET coupon_id=NULL,coupon_discount=0,updated_at=UTC_TIMESTAMP() WHERE id=:id
                """).param("id", cartId).update();
        return new MoveToCartResponse(cartId, cartItemId, item.slug(), size, finalQuantity, true);
    }

    private long ensureWishlist(long customerId) {
        jdbc.sql("SELECT id FROM users WHERE id=:id FOR UPDATE").param("id", customerId).query(Long.class).single();
        var existing = jdbc.sql("SELECT id FROM wishlists WHERE user_id=:userId FOR UPDATE")
                .param("userId", customerId).query(Long.class).optional();
        if (existing.isPresent()) return existing.get();
        var keys = new GeneratedKeyHolder();
        namedJdbc.update("INSERT INTO wishlists(user_id,created_at) VALUES(:userId,UTC_TIMESTAMP())",
                new MapSqlParameterSource("userId", customerId), keys, new String[]{"id"});
        return keys.getKey().longValue();
    }

    private long ensureCart(long customerId) {
        var existing = jdbc.sql("SELECT id FROM carts WHERE user_id=:userId ORDER BY id DESC LIMIT 1 FOR UPDATE")
                .param("userId", customerId).query(Long.class).optional();
        if (existing.isPresent()) return existing.get();
        var keys = new GeneratedKeyHolder();
        namedJdbc.update("""
                INSERT INTO carts(user_id,coupon_id,coupon_discount,created_at,updated_at)
                VALUES(:userId,NULL,0,UTC_TIMESTAMP(),UTC_TIMESTAMP())
                """, new MapSqlParameterSource("userId", customerId), keys, new String[]{"id"});
        return keys.getKey().longValue();
    }

    private Product activeProduct(String slug) {
        return jdbc.sql("""
                SELECT p.id,p.slug FROM products p
                JOIN categories c ON c.id=p.category_id AND c.is_active=1
                JOIN subcategories sc ON sc.id=p.subcategory_id AND sc.is_active=1
                WHERE p.slug=:slug AND p.is_active=1
                """).param("slug", slug).query((rs, rowNum) -> new Product(
                        rs.getLong("id"), rs.getString("slug"))).optional()
                .orElseThrow(() -> ApiException.notFound("Product"));
    }

    private void validateConfiguredSize(long productId, String size) {
        if (jdbc.sql("SELECT COUNT(*) FROM product_sizes WHERE product_id=:productId AND size=:size")
                .param("productId", productId).param("size", size).query(Integer.class).single() == 0) {
            throw ApiException.badRequest("INVALID_SIZE", "The selected size is not configured for this product");
        }
    }

    private Long findItem(long wishlistId, long productId, String size, boolean forUpdate) {
        return jdbc.sql("""
                SELECT id FROM wishlist_items
                WHERE wishlist_id=:wishlistId AND product_id=:productId AND size <=> :size
                """ + (forUpdate ? " FOR UPDATE" : "")).param("wishlistId", wishlistId)
                .param("productId", productId).param("size", size, Types.VARCHAR)
                .query(Long.class).optional().orElse(null);
    }

    private WishlistItemResponse itemById(long customerId, long itemId) {
        return get(customerId).items().stream().filter(item -> item.id() == itemId).findFirst()
                .orElseThrow(() -> ApiException.notFound("Wishlist item"));
    }

    private static WishlistItemResponse item(ResultSet rs) throws SQLException {
        long price = rs.getLong("price");
        Long discount = nullableLong(rs, "discount_price");
        return new WishlistItemResponse(rs.getLong("id"), rs.getLong("product_id"), rs.getString("sku"),
                rs.getString("slug"), rs.getString("name"), rs.getString("size"), price, discount,
                CatalogPricing.effectivePrice(price, discount), rs.getString("primary_image_url"),
                rs.getBoolean("product_available"), rs.getBoolean("selected_size_available"),
                rs.getTimestamp("added_at").toInstant());
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim().toUpperCase(java.util.Locale.ROOT); }
    private record Product(long id, String slug) {}
    private record Stock(int quantity, boolean available) {}
    private record ExistingCartItem(long id, int quantity) {}
    private record MoveItem(long id, String size, long productId, String slug, long price, Long discountPrice,
                            boolean productActive, boolean categoryActive, boolean subcategoryActive) {}
}
