package com.moodbuds.order;

import static com.moodbuds.order.api.OrderDtos.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.Year;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodbuds.cart.CartCalculations;
import com.moodbuds.cart.CartService;
import com.moodbuds.cart.api.CartDtos.CartItemResponse;
import com.moodbuds.common.ApiException;
import com.moodbuds.common.PageResponse;
import com.moodbuds.coupon.CouponCalculations;
import com.moodbuds.coupon.CouponService;
import com.moodbuds.customer.CustomerProfileService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    private final JdbcClient jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final ObjectMapper objectMapper;
    private final CartService carts;
    private final CouponService coupons;
    private final CustomerProfileService customers;
    private final OrderLifecycleProperties lifecycleProperties;

    public OrderService(JdbcClient jdbc, NamedParameterJdbcTemplate namedJdbc, ObjectMapper objectMapper,
                        CartService carts, CouponService coupons, CustomerProfileService customers,
                        OrderLifecycleProperties lifecycleProperties) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
        this.objectMapper = objectMapper;
        this.carts = carts;
        this.coupons = coupons;
        this.customers = customers;
        this.lifecycleProperties = lifecycleProperties;
    }

    @Transactional
    public OrderDetailResponse place(long customerId, String idempotencyKey, PlaceOrderRequest request) {
        customers.requireActive(customerId);
        if (request.paymentMethod() == PaymentMethod.COD) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "COD_NOT_AVAILABLE",
                    "Cash on delivery is not enabled yet");
        }
        String key = idempotencyKey.trim();
        String fingerprint = fingerprint(request);
        jdbc.sql("SELECT id FROM users WHERE id=:id FOR UPDATE").param("id", customerId).query(Long.class).single();
        var existing = jdbc.sql("""
                SELECT order_number,idempotency_fingerprint FROM orders
                WHERE user_id=:userId AND idempotency_key=:key
                """).param("userId", customerId).param("key", key)
                .query((rs, rowNum) -> new ExistingOrder(rs.getString("order_number"),
                        rs.getString("idempotency_fingerprint"))).optional().orElse(null);
        if (existing != null) {
            if (!fingerprint.equals(existing.fingerprint())) {
                throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                        "This Idempotency-Key was already used with different order details");
            }
            return get(customerId, existing.orderNumber());
        }

        var address = customers.address(customerId, request.addressId());
        boolean hadCoupon = jdbc.sql("""
                SELECT coupon_id IS NOT NULL FROM carts WHERE user_id=:userId ORDER BY id DESC LIMIT 1
                """).param("userId", customerId).query(Boolean.class).optional().orElse(false);
        var validation = carts.validate(customerId);
        if (!validation.valid()) {
            throw new ApiException(HttpStatus.CONFLICT, "CART_INVALID",
                    "Resolve the blocking cart issues before placing the order");
        }
        if (validation.cart().items().isEmpty()) {
            throw ApiException.badRequest("EMPTY_CART", "Add at least one item before placing the order");
        }
        var couponState = coupons.revalidate(customerId);
        if (hadCoupon && !couponState.applied()) {
            throw new ApiException(HttpStatus.CONFLICT, "COUPON_REAPPLY_REQUIRED",
                    "The applied coupon is no longer valid for the refreshed cart");
        }
        var cart = couponState.cart();
        var totals = couponState.totals();
        lockAndRequireStock(cart.items());

        String orderNumber = generateOrderNumber();
        long orderId = insertOrder(customerId, key, fingerprint, request, address, couponState, orderNumber);
        List<Long> discountAllocations = CouponCalculations.allocateDiscount(
                cart.items().stream().map(CartItemResponse::lineSubtotal).toList(), totals.couponDiscount());
        for (int index = 0; index < cart.items().size(); index++) {
            var item = cart.items().get(index);
            long lineGst = CartCalculations.gst(item.lineSubtotal() - discountAllocations.get(index),
                    item.gstRatePercentage());
            insertOrderItem(orderId, item, lineGst);
            reserveStock(item, orderNumber);
        }
        jdbc.sql("""
                INSERT INTO order_status_history(order_id,from_status,to_status,notes,changed_by_id,created_at)
                VALUES(:orderId,NULL,'PENDING_PAYMENT','Order placed; awaiting payment provider',:customerId,UTC_TIMESTAMP())
                """).param("orderId", orderId).param("customerId", customerId).update();
        if (couponState.applied()) {
            jdbc.sql("""
                    INSERT INTO coupon_usage(coupon_id,user_id,order_id,discount_applied,used_at)
                    VALUES(:couponId,:userId,:orderId,:discount,UTC_TIMESTAMP())
                    """).param("couponId", couponState.coupon().id()).param("userId", customerId)
                    .param("orderId", orderId).param("discount", totals.couponDiscount()).update();
            jdbc.sql("UPDATE coupons SET current_usage_count=current_usage_count+1,updated_at=UTC_TIMESTAMP() WHERE id=:id")
                    .param("id", couponState.coupon().id()).update();
        }
        jdbc.sql("DELETE FROM cart_items WHERE cart_id=:cartId").param("cartId", cart.cartId()).update();
        jdbc.sql("""
                UPDATE carts SET coupon_id=NULL,coupon_discount=0,updated_at=UTC_TIMESTAMP() WHERE id=:cartId
                """).param("cartId", cart.cartId()).update();
        return get(customerId, orderNumber);
    }

    public PageResponse<OrderSummaryResponse> list(long customerId, OrderStatus status, int page, int size) {
        customers.requireActive(customerId);
        int boundedSize = Math.min(Math.max(size, 1), 100);
        int boundedPage = Math.max(page, 0);
        String statusClause = status == null ? "" : " AND o.status=:status";
        var query = jdbc.sql("""
                SELECT o.order_number,o.status,o.payment_method,o.total_amount,o.created_at,o.updated_at,
                       o.payment_expires_at,
                       COALESCE(SUM(oi.quantity),0) total_quantity,COUNT(oi.id) item_count,
                       (SELECT JSON_UNQUOTE(JSON_EXTRACT(oi2.product_snapshot,'$.primaryImageUrl'))
                        FROM order_items oi2 WHERE oi2.order_id=o.id ORDER BY oi2.id LIMIT 1) primary_image_url
                FROM orders o LEFT JOIN order_items oi ON oi.order_id=o.id
                WHERE o.user_id=:userId
                """ + statusClause + " GROUP BY o.id ORDER BY o.id DESC LIMIT :limit OFFSET :offset")
                .param("userId", customerId).param("limit", boundedSize).param("offset", boundedPage * boundedSize);
        var count = jdbc.sql("SELECT COUNT(*) FROM orders o WHERE o.user_id=:userId" + statusClause)
                .param("userId", customerId);
        if (status != null) {
            query = query.param("status", status.name());
            count = count.param("status", status.name());
        }
        var content = query.query((rs, rowNum) -> new OrderSummaryResponse(rs.getString("order_number"),
                OrderStatus.valueOf(rs.getString("status")), paymentMethod(rs.getString("payment_method")),
                rs.getInt("item_count"), rs.getInt("total_quantity"), rs.getString("primary_image_url"),
                rs.getLong("total_amount"), paymentRequired(rs.getString("status"), rs.getString("payment_method"),
                        instant(rs, "payment_expires_at")),
                instant(rs, "created_at"), instant(rs, "updated_at"), instant(rs, "payment_expires_at"))).list();
        return PageResponse.of(content, boundedPage, boundedSize, count.query(Long.class).single());
    }

    public OrderDetailResponse get(long customerId, String orderNumber) {
        customers.requireActive(customerId);
        OrderRow order = jdbc.sql("""
                SELECT o.*,c.code coupon_code FROM orders o
                LEFT JOIN coupons c ON c.id=o.coupon_id
                WHERE o.user_id=:userId AND o.order_number=:orderNumber
                """).param("userId", customerId).param("orderNumber", orderNumber)
                .query((rs, rowNum) -> order(rs)).optional().orElseThrow(() -> ApiException.notFound("Order"));
        var items = jdbc.sql("""
                SELECT id,product_id,product_snapshot,size,quantity,unit_price,unit_discount_price,
                       gst_rate_percentage,gst_amount,line_total
                FROM order_items WHERE order_id=:orderId ORDER BY id
                """).param("orderId", order.id()).query((rs, rowNum) -> new OrderItemResponse(rs.getLong("id"),
                        rs.getLong("product_id"), json(rs.getString("product_snapshot")), rs.getString("size"),
                        rs.getInt("quantity"), rs.getLong("unit_price"), nullableLong(rs, "unit_discount_price"),
                        rs.getBigDecimal("gst_rate_percentage"), rs.getLong("gst_amount"), rs.getLong("line_total"))).list();
        var history = jdbc.sql("""
                SELECT from_status,to_status,notes,created_at FROM order_status_history
                WHERE order_id=:orderId ORDER BY id
                """).param("orderId", order.id()).query((rs, rowNum) -> new OrderStatusEvent(rs.getString("from_status"),
                        rs.getString("to_status"), rs.getString("notes"), instant(rs, "created_at"))).list();
        var payments = jdbc.sql("""
                SELECT gateway,method,status,amount,currency,initiated_at,completed_at
                FROM payments WHERE order_id=:orderId ORDER BY id
                """).param("orderId", order.id()).query((rs, rowNum) -> new PaymentSummary(rs.getString("gateway"),
                        rs.getString("method"), rs.getString("status"), rs.getLong("amount"),
                        rs.getString("currency"), instant(rs, "initiated_at"), nullableInstant(rs, "completed_at"))).list();
        return new OrderDetailResponse(order.orderNumber(), OrderStatus.valueOf(order.status()),
                paymentMethod(order.paymentMethod()), json(order.addressSnapshot()), order.couponCode(),
                order.subtotal(), order.couponDiscount(), order.shippingCost(), order.gstAmount(), order.totalAmount(),
                paymentRequired(order.status(), order.paymentMethod(), order.paymentExpiresAt()), true,
                order.shippingPricingStatus(), order.shippingPricingSource(), items, history, payments,
                order.createdAt(), order.updatedAt(), order.paymentExpiresAt(), order.cancelledAt(),
                order.cancellationReason(), directlyCancellable(order.status()));
    }

    private long insertOrder(long customerId, String key, String fingerprint, PlaceOrderRequest request,
                             Object address, com.moodbuds.coupon.api.CouponDtos.CartCouponResponse couponState,
                             String orderNumber) {
        var totals = couponState.totals();
        var params = new MapSqlParameterSource().addValue("orderNumber", orderNumber)
                .addValue("userId", customerId).addValue("key", key).addValue("fingerprint", fingerprint)
                .addValue("addressId", request.addressId()).addValue("address", jsonString(address))
                .addValue("paymentMethod", request.paymentMethod() == null ? null : request.paymentMethod().name(), Types.VARCHAR)
                .addValue("couponId", couponState.applied() ? couponState.coupon().id() : null, Types.BIGINT)
                .addValue("subtotal", totals.sellingSubtotal()).addValue("couponDiscount", totals.couponDiscount())
                .addValue("gst", totals.gstAmount()).addValue("total", totals.grandTotal())
                .addValue("paymentExpiresAt", Instant.now().plus(lifecycleProperties.paymentTimeout()));
        var keys = new GeneratedKeyHolder();
        namedJdbc.update("""
                INSERT INTO orders(order_number,user_id,idempotency_key,idempotency_fingerprint,status,
                    shipping_address_id,shipping_address_snapshot_full,payment_method,coupon_id,subtotal,
                    coupon_discount,shipping_cost,shipping_pricing_status,shipping_pricing_source,
                    gst_amount,total_amount,payment_expires_at,created_at,updated_at)
                VALUES(:orderNumber,:userId,:key,:fingerprint,'PENDING_PAYMENT',:addressId,:address,
                    :paymentMethod,:couponId,:subtotal,:couponDiscount,0,'FINALIZED','FREE_SHIPPING_TEMPORARY',
                    :gst,:total,:paymentExpiresAt,UTC_TIMESTAMP(),UTC_TIMESTAMP())
                """, params, keys, new String[]{"id"});
        return keys.getKey().longValue();
    }

    private void insertOrderItem(long orderId, CartItemResponse item, long gst) {
        jdbc.sql("""
                INSERT INTO order_items(order_id,product_id,product_snapshot,size,quantity,unit_price,
                    unit_discount_price,gst_rate_percentage,gst_amount,line_total,return_window_days_snapshot)
                VALUES(:orderId,:productId,:snapshot,:size,:quantity,:price,:discount,:gstRate,:gst,:lineTotal,
                    :returnWindowDays)
                """).param("orderId", orderId).param("productId", item.productId())
                .param("snapshot", jsonString(productSnapshot(item.productId())))
                .param("size", item.size()).param("quantity", item.quantity())
                .param("price", item.currentUnitPrice())
                .param("discount", item.currentUnitDiscountPrice(), Types.INTEGER)
                .param("gstRate", item.gstRatePercentage()).param("gst", gst)
                .param("lineTotal", item.lineSubtotal()).param("returnWindowDays", returnWindowDays(item.productId()))
                .update();
    }

    private int returnWindowDays(long productId) {
        return jdbc.sql("SELECT return_window_days FROM products WHERE id=:id")
                .param("id", productId).query(Integer.class).single();
    }

    private Object productSnapshot(long productId) {
        return jdbc.sql("""
                SELECT p.id productId,p.sku,p.slug,p.name,p.description,p.fabric_details fabricDetails,
                       p.color_name colorName,p.weight_grams weightGrams,
                       (SELECT pi.image_url FROM product_images pi WHERE pi.product_id=p.id
                        ORDER BY pi.is_primary DESC,pi.id LIMIT 1) primaryImageUrl
                FROM products p WHERE p.id=:id
                """).param("id", productId).query().singleRow();
    }

    private void lockAndRequireStock(List<CartItemResponse> items) {
        for (var item : items) {
            var stock = jdbc.sql("""
                    SELECT stock_quantity,is_available FROM product_sizes
                    WHERE product_id=:productId AND size=:size FOR UPDATE
                    """).param("productId", item.productId()).param("size", item.size())
                    .query((rs, rowNum) -> new Stock(rs.getInt("stock_quantity"), rs.getBoolean("is_available")))
                    .optional().orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "SIZE_UNAVAILABLE",
                            "A selected product size is no longer available"));
            if (!stock.available() || stock.quantity() < item.quantity()) {
                throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK",
                        "A selected product no longer has enough stock");
            }
        }
    }

    private void reserveStock(CartItemResponse item, String orderNumber) {
        int before = jdbc.sql("""
                SELECT stock_quantity FROM product_sizes WHERE product_id=:productId AND size=:size FOR UPDATE
                """).param("productId", item.productId()).param("size", item.size()).query(Integer.class).single();
        int after = before - item.quantity();
        jdbc.sql("""
                UPDATE product_sizes SET stock_quantity=:after,is_available=:available,updated_at=UTC_TIMESTAMP()
                WHERE product_id=:productId AND size=:size
                """).param("after", after).param("available", after > 0)
                .param("productId", item.productId()).param("size", item.size()).update();
        jdbc.sql("""
                INSERT INTO inventory_logs(product_id,size,change_type,quantity_change,quantity_before,
                    quantity_after,reference_type,created_by,created_at)
                VALUES(:productId,:size,'RESERVATION',:change,:before,:after,:reference,NULL,UTC_TIMESTAMP())
                """).param("productId", item.productId()).param("size", item.size())
                .param("change", -item.quantity()).param("before", before).param("after", after)
                .param("reference", "order:" + orderNumber).update();
    }

    private OrderRow order(ResultSet rs) throws SQLException {
        return new OrderRow(rs.getLong("id"), rs.getString("order_number"), rs.getString("status"),
                rs.getString("payment_method"), rs.getString("shipping_address_snapshot_full"),
                rs.getString("coupon_code"), rs.getLong("subtotal"), rs.getLong("coupon_discount"),
                rs.getLong("shipping_cost"), rs.getLong("gst_amount"), rs.getLong("total_amount"),
                rs.getString("shipping_pricing_status"), rs.getString("shipping_pricing_source"),
                instant(rs, "created_at"), instant(rs, "updated_at"), instant(rs, "payment_expires_at"),
                nullableInstant(rs, "cancelled_at"), rs.getString("cancellation_reason"));
    }

    private JsonNode json(String value) {
        try { return objectMapper.readTree(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Invalid persisted JSON", exception); }
    }

    private String jsonString(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Could not create order snapshot", exception); }
    }

    private static String generateOrderNumber() {
        return "MB-" + Year.now().getValue() + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private static String fingerprint(PlaceOrderRequest request) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    (request.addressId() + "|" + (request.paymentMethod() == null ? "RAZORPAY" : request.paymentMethod().name()))
                            .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static boolean paymentRequired(String status, String method, Instant paymentExpiresAt) {
        return !"COD".equals(method) && paymentExpiresAt.isAfter(Instant.now())
                && ("PENDING_PAYMENT".equals(status) || "PAYMENT_FAILED".equals(status));
    }
    private static boolean directlyCancellable(String status) {
        return "PENDING_PAYMENT".equals(status) || "PAYMENT_FAILED".equals(status);
    }
    private static PaymentMethod paymentMethod(String method) {
        return method == null ? null : PaymentMethod.valueOf(method);
    }
    private static Instant instant(ResultSet rs, String field) throws SQLException { return rs.getTimestamp(field).toInstant(); }
    private static Instant nullableInstant(ResultSet rs, String field) throws SQLException {
        var timestamp = rs.getTimestamp(field); return timestamp == null ? null : timestamp.toInstant();
    }
    private static Long nullableLong(ResultSet rs, String field) throws SQLException {
        long value = rs.getLong(field); return rs.wasNull() ? null : value;
    }

    private record ExistingOrder(String orderNumber, String fingerprint) {}
    private record Stock(int quantity, boolean available) {}
    private record OrderRow(long id, String orderNumber, String status, String paymentMethod,
                            String addressSnapshot, String couponCode, long subtotal, long couponDiscount,
                            long shippingCost, long gstAmount, long totalAmount,
                            String shippingPricingStatus, String shippingPricingSource,
                            Instant createdAt, Instant updatedAt, Instant paymentExpiresAt,
                            Instant cancelledAt, String cancellationReason) {}
}
