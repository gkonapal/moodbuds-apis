package com.moodbuds.cart;

import static com.moodbuds.cart.api.CartDtos.*;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
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
public class CartService {
    public static final int MAX_ITEM_QUANTITY = 10;

    private final JdbcClient jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final CustomerProfileService customers;

    public CartService(JdbcClient jdbc, NamedParameterJdbcTemplate namedJdbc, CustomerProfileService customers) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
        this.customers = customers;
    }

    public CartResponse get(long customerId) {
        customers.requireActive(customerId);
        var cart = jdbc.sql("SELECT id,updated_at FROM carts WHERE user_id=:userId ORDER BY id DESC LIMIT 1")
                .param("userId", customerId).query((rs, rowNum) -> new CartIdentity(
                        rs.getLong("id"), rs.getTimestamp("updated_at").toInstant())).optional().orElse(null);
        if (cart == null) return emptyCart();
        List<ItemRow> rows = jdbc.sql("""
                SELECT ci.id,ci.product_id,ci.size,ci.quantity,ci.unit_price,ci.unit_discount_price,
                       p.sku,p.slug,p.name,p.price,p.discount_price,(p.is_active=1 AND p.publication_status='PUBLISHED') is_active,
                       c.is_active category_active,sc.is_active subcategory_active,
                       g.rate_percentage,g.is_active gst_active,
                       ps.id product_size_id,ps.stock_quantity,ps.is_available size_active,
                       (SELECT pi.image_url FROM product_images pi WHERE pi.product_id=p.id
                        ORDER BY pi.is_primary DESC,pi.id LIMIT 1) primary_image_url
                FROM cart_items ci
                JOIN products p ON p.id=ci.product_id
                JOIN categories c ON c.id=p.category_id
                JOIN subcategories sc ON sc.id=p.subcategory_id
                JOIN gst_rates g ON g.id=p.gst_rate_id
                LEFT JOIN product_sizes ps ON ps.product_id=p.id AND ps.size=ci.size
                WHERE ci.cart_id=:cartId
                ORDER BY ci.id DESC
                """).param("cartId", cart.id()).query((rs, rowNum) -> itemRow(rs)).list();
        List<CartItemResponse> items = rows.stream().map(this::itemResponse).toList();
        return response(cart.id(), cart.updatedAt(), items);
    }

    public CartResponse summary(long customerId) {
        return get(customerId);
    }

    public CartCountResponse count(long customerId) {
        var cart = get(customerId);
        return new CartCountResponse(cart.distinctItemCount(), cart.totalQuantity());
    }

    @Transactional
    public CartResponse add(long customerId, AddCartItemRequest request) {
        customers.requireActive(customerId);
        lockCustomer(customerId);
        String slug = request.productSlug().trim();
        String size = normalizeSize(request.size());
        var product = availableProduct(slug, size);
        long cartId = ensureCart(customerId);
        var existing = jdbc.sql("""
                SELECT id,quantity FROM cart_items
                WHERE cart_id=:cartId AND product_id=:productId AND size=:size FOR UPDATE
                """).param("cartId", cartId).param("productId", product.id()).param("size", size)
                .query((rs, rowNum) -> new ExistingItem(rs.getLong("id"), rs.getInt("quantity"))).optional().orElse(null);
        int finalQuantity = existing == null ? request.quantity() : existing.quantity();
        requireQuantity(finalQuantity);
        requireStock(product, finalQuantity);
        Long discount = validDiscount(product.price(), product.discountPrice());
        if (existing == null) {
            namedJdbc.update("""
                    INSERT INTO cart_items(cart_id,product_id,size,quantity,unit_price,unit_discount_price)
                    VALUES(:cartId,:productId,:size,:quantity,:price,:discount)
                    """, new MapSqlParameterSource().addValue("cartId", cartId).addValue("productId", product.id())
                    .addValue("size", size).addValue("quantity", finalQuantity).addValue("price", product.price())
                    .addValue("discount", discount, Types.INTEGER));
        } else {
            jdbc.sql("""
                    UPDATE cart_items SET quantity=:quantity,unit_price=:price,unit_discount_price=:discount
                    WHERE id=:id
                    """).param("quantity", finalQuantity).param("price", product.price())
                    .param("discount", discount, Types.INTEGER).param("id", existing.id()).update();
        }
        touchAndInvalidateCoupon(cartId);
        return get(customerId);
    }

    @Transactional
    public CartResponse update(long customerId, long itemId, UpdateCartItemRequest request) {
        customers.requireActive(customerId);
        if (request.size() == null && request.quantity() == null) {
            throw ApiException.badRequest("EMPTY_REQUEST", "Provide size, quantity, or both");
        }
        lockCustomer(customerId);
        var source = ownedItem(customerId, itemId, true);
        String targetSize = request.size() == null ? source.size() : normalizeSize(request.size());
        int requestedQuantity = request.quantity() == null ? source.quantity() : request.quantity();
        requireQuantity(requestedQuantity);
        var product = availableProductById(source.productId(), targetSize);
        long targetId = source.id();
        int finalQuantity = requestedQuantity;
        if (!targetSize.equals(source.size())) {
            var target = jdbc.sql("""
                    SELECT id,quantity FROM cart_items
                    WHERE cart_id=:cartId AND product_id=:productId AND size=:size FOR UPDATE
                    """).param("cartId", source.cartId()).param("productId", source.productId()).param("size", targetSize)
                    .query((rs, rowNum) -> new ExistingItem(rs.getLong("id"), rs.getInt("quantity"))).optional().orElse(null);
            if (target != null) {
                targetId = target.id();
                finalQuantity += target.quantity();
            }
        }
        requireQuantity(finalQuantity);
        requireStock(product, finalQuantity);
        Long discount = validDiscount(product.price(), product.discountPrice());
        if (targetId != source.id()) {
            jdbc.sql("""
                    UPDATE cart_items SET quantity=:quantity,unit_price=:price,unit_discount_price=:discount
                    WHERE id=:id
                    """).param("quantity", finalQuantity).param("price", product.price())
                    .param("discount", discount, Types.INTEGER).param("id", targetId).update();
            jdbc.sql("DELETE FROM cart_items WHERE id=:id").param("id", source.id()).update();
        } else {
            jdbc.sql("""
                    UPDATE cart_items SET size=:size,quantity=:quantity,unit_price=:price,unit_discount_price=:discount
                    WHERE id=:id
                    """).param("size", targetSize).param("quantity", finalQuantity).param("price", product.price())
                    .param("discount", discount, Types.INTEGER).param("id", source.id()).update();
        }
        touchAndInvalidateCoupon(source.cartId());
        return get(customerId);
    }

    @Transactional
    public CartResponse remove(long customerId, long itemId) {
        customers.requireActive(customerId);
        lockCustomer(customerId);
        var item = ownedItem(customerId, itemId, true);
        jdbc.sql("DELETE FROM cart_items WHERE id=:id").param("id", itemId).update();
        touchAndInvalidateCoupon(item.cartId());
        return get(customerId);
    }

    @Transactional
    public MoveToWishlistResponse moveToWishlist(long customerId, long itemId) {
        customers.requireActive(customerId);
        lockCustomer(customerId);
        var item = ownedItem(customerId, itemId, true);
        long wishlistId = ensureWishlist(customerId);
        Long wishlistItemId = jdbc.sql("""
                SELECT id FROM wishlist_items
                WHERE wishlist_id=:wishlistId AND product_id=:productId AND size <=> :size FOR UPDATE
                """).param("wishlistId", wishlistId).param("productId", item.productId())
                .param("size", item.size(), Types.VARCHAR).query(Long.class).optional().orElse(null);
        if (wishlistItemId == null) {
            var keys = new GeneratedKeyHolder();
            namedJdbc.update("""
                    INSERT INTO wishlist_items(wishlist_id,product_id,size,added_at)
                    VALUES(:wishlistId,:productId,:size,UTC_TIMESTAMP())
                    """, new MapSqlParameterSource().addValue("wishlistId", wishlistId)
                    .addValue("productId", item.productId()).addValue("size", item.size()),
                    keys, new String[]{"id"});
            wishlistItemId = keys.getKey().longValue();
        }
        jdbc.sql("DELETE FROM cart_items WHERE id=:id").param("id", item.id()).update();
        touchAndInvalidateCoupon(item.cartId());
        String slug = jdbc.sql("SELECT slug FROM products WHERE id=:id").param("id", item.productId())
                .query(String.class).single();
        return new MoveToWishlistResponse(wishlistId, wishlistItemId, slug, item.size(), true, get(customerId));
    }

    @Transactional
    public CartResponse clear(long customerId) {
        customers.requireActive(customerId);
        lockCustomer(customerId);
        Long cartId = findCartId(customerId, true);
        if (cartId == null) return emptyCart();
        jdbc.sql("DELETE FROM cart_items WHERE cart_id=:id").param("id", cartId).update();
        touchAndInvalidateCoupon(cartId);
        return get(customerId);
    }

    @Transactional
    public CartValidationResponse validate(long customerId) {
        customers.requireActive(customerId);
        lockCustomer(customerId);
        Long cartId = findCartId(customerId, true);
        if (cartId == null) return new CartValidationResponse(true, false, List.of(), emptyCart());
        jdbc.sql("SELECT id FROM cart_items WHERE cart_id=:cartId FOR UPDATE")
                .param("cartId", cartId).query(Long.class).list();
        var before = get(customerId);
        var issues = new ArrayList<CartIssue>();
        boolean refreshed = false;
        for (var item : before.items()) {
            if (!item.productAvailable()) {
                issues.add(new CartIssue(item.id(), item.productSlug(), "PRODUCT_UNAVAILABLE",
                        "The product is no longer available", true));
                continue;
            }
            if (!item.sizeConfigured()) {
                issues.add(new CartIssue(item.id(), item.productSlug(), "SIZE_UNAVAILABLE",
                        "The selected size is no longer configured", true));
                continue;
            }
            if (!item.requestedQuantityAvailable()) {
                issues.add(new CartIssue(item.id(), item.productSlug(), "INSUFFICIENT_STOCK",
                        "The requested quantity is no longer available", true));
                continue;
            }
            if (item.priceChanged()) {
                refreshed = true;
                issues.add(new CartIssue(item.id(), item.productSlug(), "PRICE_UPDATED",
                        "The item price was updated to the current catalog price", false));
                jdbc.sql("""
                        UPDATE cart_items SET unit_price=:price,unit_discount_price=:discount WHERE id=:id
                        """).param("price", item.currentUnitPrice())
                        .param("discount", validDiscount(item.currentUnitPrice(), item.currentUnitDiscountPrice()), Types.INTEGER)
                        .param("id", item.id()).update();
            }
        }
        if (refreshed) touchAndInvalidateCoupon(cartId);
        boolean valid = issues.stream().noneMatch(CartIssue::blocking);
        return new CartValidationResponse(valid, refreshed, List.copyOf(issues), get(customerId));
    }

    private CartResponse response(long cartId, Instant updatedAt, List<CartItemResponse> items) {
        int quantity = items.stream().mapToInt(CartItemResponse::quantity).sum();
        long mrp = items.stream().mapToLong(item -> item.currentUnitPrice() * item.quantity()).sum();
        long selling = items.stream().mapToLong(CartItemResponse::lineSubtotal).sum();
        long gst = items.stream().mapToLong(CartItemResponse::gstAmount).sum();
        return new CartResponse(cartId, items.size(), quantity, items,
                new CartTotals(mrp, mrp - selling, selling, gst, selling), updatedAt);
    }

    private CartResponse emptyCart() {
        return new CartResponse(null, 0, 0, List.of(), new CartTotals(0, 0, 0, 0, 0), null);
    }

    private CartItemResponse itemResponse(ItemRow row) {
        Long currentDiscount = validDiscount(row.currentPrice(), row.currentDiscountPrice());
        long effective = CatalogPricing.effectivePrice(row.currentPrice(), currentDiscount);
        long subtotal = effective * row.quantity();
        long gst = CartCalculations.gst(subtotal, row.gstRate());
        Long addedDiscount = validDiscount(row.addedPrice(), row.addedDiscountPrice());
        boolean priceChanged = row.addedPrice() != row.currentPrice()
                || !java.util.Objects.equals(addedDiscount, currentDiscount);
        boolean productAvailable = row.productActive() && row.categoryActive()
                && row.subcategoryActive() && row.gstActive();
        boolean sizeConfigured = row.productSizeId() != null;
        boolean quantityAvailable = sizeConfigured && row.sizeActive() && row.stockQuantity() >= row.quantity();
        return new CartItemResponse(row.id(), row.productId(), row.sku(), row.slug(), row.name(), row.imageUrl(),
                row.size(), row.quantity(), row.addedPrice(), addedDiscount, row.currentPrice(), currentDiscount,
                effective, priceChanged, row.gstRate(), gst, subtotal, subtotal,
                productAvailable, sizeConfigured, quantityAvailable);
    }

    private ProductSelection availableProduct(String slug, String size) {
        return jdbc.sql(productSelectionSql("p.slug=:slug"))
                .param("slug", slug).param("size", size)
                .query((rs, rowNum) -> productSelection(rs)).optional().orElseThrow(() -> ApiException.notFound("Product or size"));
    }

    private ProductSelection availableProductById(long productId, String size) {
        return jdbc.sql(productSelectionSql("p.id=:productId"))
                .param("productId", productId).param("size", size)
                .query((rs, rowNum) -> productSelection(rs)).optional().orElseThrow(() -> ApiException.notFound("Product or size"));
    }

    private String productSelectionSql(String predicate) {
        return """
                SELECT p.id,p.price,p.discount_price,ps.stock_quantity,ps.is_available
                FROM products p
                JOIN categories c ON c.id=p.category_id AND c.is_active=1
                JOIN subcategories sc ON sc.id=p.subcategory_id AND sc.is_active=1
                JOIN gst_rates g ON g.id=p.gst_rate_id AND g.is_active=1
                JOIN product_sizes ps ON ps.product_id=p.id AND ps.size=:size
                WHERE p.is_active=1 AND p.publication_status='PUBLISHED' AND
                """ + predicate + " FOR UPDATE";
    }

    private ProductSelection productSelection(ResultSet rs) throws SQLException {
        return new ProductSelection(rs.getLong("id"), rs.getLong("price"), nullableLong(rs, "discount_price"),
                rs.getInt("stock_quantity"), rs.getBoolean("is_available"));
    }

    private OwnedItem ownedItem(long customerId, long itemId, boolean forUpdate) {
        return jdbc.sql("""
                SELECT ci.id,ci.cart_id,ci.product_id,ci.size,ci.quantity
                FROM cart_items ci JOIN carts c ON c.id=ci.cart_id
                WHERE ci.id=:itemId AND c.user_id=:userId
                """ + (forUpdate ? " FOR UPDATE" : "")).param("itemId", itemId).param("userId", customerId)
                .query((rs, rowNum) -> new OwnedItem(rs.getLong("id"), rs.getLong("cart_id"),
                        rs.getLong("product_id"), rs.getString("size"), rs.getInt("quantity")))
                .optional().orElseThrow(() -> ApiException.notFound("Cart item"));
    }

    private long ensureCart(long customerId) {
        Long existing = findCartId(customerId, true);
        if (existing != null) return existing;
        var keys = new GeneratedKeyHolder();
        namedJdbc.update("""
                INSERT INTO carts(user_id,coupon_id,coupon_discount,created_at,updated_at)
                VALUES(:userId,NULL,0,UTC_TIMESTAMP(),UTC_TIMESTAMP())
                """, new MapSqlParameterSource("userId", customerId), keys, new String[]{"id"});
        return keys.getKey().longValue();
    }

    private long ensureWishlist(long customerId) {
        var existing = jdbc.sql("SELECT id FROM wishlists WHERE user_id=:userId FOR UPDATE")
                .param("userId", customerId).query(Long.class).optional();
        if (existing.isPresent()) return existing.get();
        var keys = new GeneratedKeyHolder();
        namedJdbc.update("INSERT INTO wishlists(user_id,created_at) VALUES(:userId,UTC_TIMESTAMP())",
                new MapSqlParameterSource("userId", customerId), keys, new String[]{"id"});
        return keys.getKey().longValue();
    }

    private Long findCartId(long customerId, boolean forUpdate) {
        return jdbc.sql("SELECT id FROM carts WHERE user_id=:userId ORDER BY id DESC LIMIT 1" + (forUpdate ? " FOR UPDATE" : ""))
                .param("userId", customerId).query(Long.class).optional().orElse(null);
    }

    private void lockCustomer(long customerId) {
        jdbc.sql("SELECT id FROM users WHERE id=:id FOR UPDATE").param("id", customerId).query(Long.class).single();
    }

    private void touchAndInvalidateCoupon(long cartId) {
        jdbc.sql("""
                UPDATE carts SET coupon_id=NULL,coupon_discount=0,updated_at=UTC_TIMESTAMP() WHERE id=:id
                """).param("id", cartId).update();
    }

    private void requireQuantity(int quantity) {
        if (quantity < 1 || quantity > MAX_ITEM_QUANTITY) {
            throw ApiException.badRequest("CART_QUANTITY_LIMIT", "A cart item quantity must be between 1 and 10");
        }
    }

    private void requireStock(ProductSelection product, int quantity) {
        if (!product.sizeActive() || product.stockQuantity() < quantity) {
            throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", "The requested quantity is not available");
        }
    }

    private static String normalizeSize(String value) { return value.trim().toUpperCase(java.util.Locale.ROOT); }
    private static Long validDiscount(long price, Long discount) { return discount != null && discount < price ? discount : null; }

    private static ItemRow itemRow(ResultSet rs) throws SQLException {
        long sizeId = rs.getLong("product_size_id");
        Long productSizeId = rs.wasNull() ? null : sizeId;
        return new ItemRow(rs.getLong("id"), rs.getLong("product_id"), rs.getString("sku"), rs.getString("slug"),
                rs.getString("name"), rs.getString("primary_image_url"), rs.getString("size"), rs.getInt("quantity"),
                rs.getLong("unit_price"), nullableLong(rs, "unit_discount_price"), rs.getLong("price"),
                nullableLong(rs, "discount_price"), rs.getBigDecimal("rate_percentage"), rs.getBoolean("is_active"),
                rs.getBoolean("category_active"), rs.getBoolean("subcategory_active"), rs.getBoolean("gst_active"),
                productSizeId, rs.getInt("stock_quantity"), rs.getBoolean("size_active"));
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private record CartIdentity(long id, Instant updatedAt) {}
    private record ExistingItem(long id, int quantity) {}
    private record OwnedItem(long id, long cartId, long productId, String size, int quantity) {}
    private record ProductSelection(long id, long price, Long discountPrice, int stockQuantity, boolean sizeActive) {}
    private record ItemRow(long id, long productId, String sku, String slug, String name, String imageUrl,
                           String size, int quantity, long addedPrice, Long addedDiscountPrice,
                           long currentPrice, Long currentDiscountPrice, BigDecimal gstRate,
                           boolean productActive, boolean categoryActive, boolean subcategoryActive,
                           boolean gstActive, Long productSizeId, int stockQuantity, boolean sizeActive) {}
}
