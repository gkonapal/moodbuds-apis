package com.moodbuds.coupon;

import static com.moodbuds.coupon.api.CouponDtos.*;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import com.moodbuds.cart.CartService;
import com.moodbuds.cart.api.CartDtos.CartResponse;
import com.moodbuds.common.ApiException;
import com.moodbuds.customer.CustomerProfileService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CouponService {
    private final JdbcClient jdbc;
    private final CartService carts;
    private final CustomerProfileService customers;

    public CouponService(JdbcClient jdbc, CartService carts, CustomerProfileService customers) {
        this.jdbc = jdbc;
        this.carts = carts;
        this.customers = customers;
    }

    public List<PublicCouponResponse> publicCoupons() {
        return jdbc.sql("""
                SELECT id,code,description,type,discount_value,min_order_value,max_discount_amount,
                       valid_from,valid_until,usage_limit_global,usage_limit_per_user,current_usage_count,
                       is_active,is_public,audience_type,first_order_only,show_on_homepage
                FROM coupons
                WHERE is_active=1 AND audience_type='PUBLIC' AND first_order_only=0 AND valid_from<=UTC_TIMESTAMP()
                  AND (valid_until IS NULL OR valid_until>=UTC_TIMESTAMP())
                  AND (usage_limit_global IS NULL OR current_usage_count<usage_limit_global)
                ORDER BY valid_until IS NULL,valid_until,id
                """).query((rs, rowNum) -> publicResponse(coupon(rs))).list();
    }

    public PublicCouponResponse publicCoupon(String code) {
        CouponRow coupon = jdbc.sql("""
                SELECT id,code,description,type,discount_value,min_order_value,max_discount_amount,
                       usage_limit_global,usage_limit_per_user,current_usage_count,is_active,is_public,
                       audience_type,first_order_only,show_on_homepage,
                       valid_from,valid_until
                FROM coupons WHERE UPPER(code)=:code AND is_active=1 AND audience_type='PUBLIC' AND first_order_only=0
                  AND valid_from<=UTC_TIMESTAMP() AND (valid_until IS NULL OR valid_until>=UTC_TIMESTAMP())
                  AND (usage_limit_global IS NULL OR current_usage_count<usage_limit_global)
                """).param("code", normalize(code)).query((rs, rowNum) -> coupon(rs)).optional()
                .orElseThrow(() -> ApiException.notFound("Coupon"));
        return publicResponse(coupon);
    }

    public CustomerCouponResponse publicHomepageCoupon() {
        CouponRow coupon = jdbc.sql("""
                SELECT id,code,description,type,discount_value,min_order_value,max_discount_amount,
                       usage_limit_global,usage_limit_per_user,current_usage_count,is_active,is_public,
                       audience_type,first_order_only,show_on_homepage,valid_from,valid_until
                FROM coupons
                WHERE is_active=1 AND audience_type='PUBLIC' AND first_order_only=1 AND show_on_homepage=1
                  AND valid_from<=UTC_TIMESTAMP() AND (valid_until IS NULL OR valid_until>=UTC_TIMESTAMP())
                  AND (usage_limit_global IS NULL OR current_usage_count<usage_limit_global)
                ORDER BY id DESC LIMIT 1
                """).query((rs,rowNum)->coupon(rs)).optional()
                .orElseThrow(()->ApiException.notFound("Homepage coupon"));
        return new CustomerCouponResponse(coupon.code(), coupon.description(), CouponType.valueOf(coupon.type()),
                coupon.value(), coupon.minimumOrder(), coupon.maximumDiscount(), coupon.validFrom(), coupon.validUntil(),
                coupon.audience(), true, true, coupon.perUserLimit());
    }

    public List<CustomerCouponResponse> customerCoupons(long customerId, boolean homepageOnly) {
        customers.requireActive(customerId);
        String homepage = homepageOnly ? " AND c.show_on_homepage=1" : "";
        return jdbc.sql("""
                SELECT c.id,c.code,c.description,c.type,c.discount_value,c.min_order_value,c.max_discount_amount,
                       c.usage_limit_global,c.usage_limit_per_user,c.current_usage_count,c.is_active,c.is_public,
                       c.audience_type,c.first_order_only,c.show_on_homepage,c.valid_from,c.valid_until
                FROM coupons c
                WHERE c.is_active=1 AND c.valid_from<=UTC_TIMESTAMP()
                  AND (c.valid_until IS NULL OR c.valid_until>=UTC_TIMESTAMP())
                  AND (c.usage_limit_global IS NULL OR c.current_usage_count<c.usage_limit_global)
                  AND (c.audience_type='PUBLIC' OR (c.audience_type='ASSIGNED_USERS' AND EXISTS(
                      SELECT 1 FROM coupon_user_assignments a WHERE a.coupon_id=c.id AND a.user_id=:userId AND a.is_active=1)))
                """ + homepage + " ORDER BY c.show_on_homepage DESC,c.valid_until IS NULL,c.valid_until,c.id")
                .param("userId", customerId).query((rs,rowNum)->coupon(rs)).list().stream()
                .map(coupon -> customerResponseIfEligible(coupon, customerId)).filter(java.util.Objects::nonNull).toList();
    }

    @Transactional
    public CartCouponResponse apply(long customerId, ApplyCouponRequest request) {
        customers.requireActive(customerId);
        var validation = carts.validate(customerId);
        if (!validation.valid()) {
            throw new ApiException(HttpStatus.CONFLICT, "CART_INVALID", "Resolve the blocking cart issues before applying a coupon");
        }
        CartResponse cart = validation.cart();
        if (cart.items().isEmpty()) {
            throw ApiException.badRequest("EMPTY_CART", "Add at least one item before applying a coupon");
        }
        CouponRow coupon = couponForUpdate(request.code());
        long discount = eligibleDiscount(coupon, customerId, cart.totals().sellingSubtotal());
        jdbc.sql("""
                UPDATE carts SET coupon_id=:couponId,coupon_discount=:discount,updated_at=UTC_TIMESTAMP()
                WHERE id=:cartId AND user_id=:userId
                """).param("couponId", coupon.id()).param("discount", discount)
                .param("cartId", cart.cartId()).param("userId", customerId).update();
        return response(cart, coupon, discount);
    }

    public CartCouponResponse get(long customerId) {
        customers.requireActive(customerId);
        CartResponse cart = carts.get(customerId);
        StoredCoupon stored = stored(customerId);
        if (stored == null) return response(cart, null, 0);
        CouponRow coupon = coupon(stored.couponId());
        long discount = eligibleDiscount(coupon, customerId, cart.totals().sellingSubtotal());
        return response(cart, coupon, discount);
    }

    @Transactional
    public CartCouponResponse revalidate(long customerId) {
        customers.requireActive(customerId);
        var validation = carts.validate(customerId);
        CartResponse cart = validation.cart();
        StoredCoupon stored = storedForUpdate(customerId);
        if (stored == null) return response(cart, null, 0);
        if (!validation.valid() || cart.items().isEmpty()) {
            clear(customerId);
            return response(cart, null, 0);
        }
        try {
            CouponRow coupon = couponForUpdate(stored.couponId());
            long discount = eligibleDiscount(coupon, customerId, cart.totals().sellingSubtotal());
            jdbc.sql("UPDATE carts SET coupon_discount=:discount,updated_at=UTC_TIMESTAMP() WHERE user_id=:userId")
                    .param("discount", discount).param("userId", customerId).update();
            return response(cart, coupon, discount);
        } catch (ApiException exception) {
            clear(customerId);
            return response(cart, null, 0);
        }
    }

    @Transactional
    public CartCouponResponse remove(long customerId) {
        customers.requireActive(customerId);
        clear(customerId);
        return response(carts.get(customerId), null, 0);
    }

    private long eligibleDiscount(CouponRow coupon, long customerId, long subtotal) {
        Instant now = Instant.now();
        if (!coupon.active() || now.isBefore(coupon.validFrom())
                || coupon.validUntil() != null && now.isAfter(coupon.validUntil())) {
            throw unprocessable("COUPON_NOT_ACTIVE", "This coupon is not currently active");
        }
        if (coupon.globalLimit() != null && coupon.currentUsage() >= coupon.globalLimit()) {
            throw unprocessable("COUPON_USAGE_LIMIT_REACHED", "This coupon has reached its usage limit");
        }
        int customerUsage = usage(coupon.id(), customerId);
        int customerLimit = customerLimit(coupon, customerId);
        if (coupon.firstOrderOnly() && !firstOrderEligible(customerId)) {
            throw unprocessable("COUPON_FIRST_ORDER_ONLY", "This offer is available only on your first order");
        }
        if (customerUsage >= customerLimit) {
            throw unprocessable("COUPON_USER_LIMIT_REACHED", "You have already used this coupon the maximum number of times");
        }
        if (subtotal < coupon.minimumOrder()) {
            throw unprocessable("COUPON_MINIMUM_NOT_MET",
                    "The cart subtotal does not meet this coupon's minimum order value");
        }
        long discount = CouponCalculations.discount(coupon.type(), coupon.value(), subtotal, coupon.maximumDiscount());
        if (discount <= 0) throw unprocessable("COUPON_NO_DISCOUNT", "This coupon does not produce a valid discount");
        return discount;
    }

    private CartCouponResponse response(CartResponse cart, CouponRow coupon, long discount) {
        long gst = CouponCalculations.gstAfterDiscount(cart.items().stream()
                .map(item -> new CouponCalculations.TaxLine(item.lineSubtotal(), item.gstRatePercentage())).toList(), discount);
        long taxable = cart.totals().sellingSubtotal() - discount;
        var totals = new CouponTotals(cart.totals().mrpSubtotal(), cart.totals().productDiscount(),
                cart.totals().sellingSubtotal(), discount, taxable, gst, null, taxable);
        AppliedCouponResponse applied = coupon == null ? null : new AppliedCouponResponse(coupon.id(), coupon.code(),
                coupon.description(), CouponType.valueOf(coupon.type()), coupon.value(), discount,
                coupon.minimumOrder(), coupon.maximumDiscount(), coupon.validFrom(), coupon.validUntil());
        return new CartCouponResponse(coupon != null, applied, totals, cart);
    }

    private StoredCoupon stored(long customerId) {
        return jdbc.sql("SELECT coupon_id,coupon_discount FROM carts WHERE user_id=:userId ORDER BY id DESC LIMIT 1")
                .param("userId", customerId).query((rs, rowNum) -> stored(rs)).optional().orElse(null);
    }

    private StoredCoupon storedForUpdate(long customerId) {
        return jdbc.sql("SELECT coupon_id,coupon_discount FROM carts WHERE user_id=:userId ORDER BY id DESC LIMIT 1 FOR UPDATE")
                .param("userId", customerId).query((rs, rowNum) -> stored(rs)).optional().orElse(null);
    }

    private StoredCoupon stored(ResultSet rs) throws SQLException {
        long id = rs.getLong("coupon_id");
        return rs.wasNull() ? null : new StoredCoupon(id, rs.getLong("coupon_discount"));
    }

    private CouponRow coupon(long couponId) {
        return jdbc.sql(selectCoupon() + " WHERE id=:id").param("id", couponId)
                .query((rs, rowNum) -> coupon(rs)).optional()
                .orElseThrow(() -> ApiException.notFound("Coupon"));
    }

    private CouponRow couponForUpdate(String code) {
        return jdbc.sql(selectCoupon() + " WHERE UPPER(code)=:code FOR UPDATE").param("code", normalize(code))
                .query((rs, rowNum) -> coupon(rs)).optional()
                .orElseThrow(() -> unprocessable("COUPON_INVALID", "The coupon code is invalid"));
    }

    private CouponRow couponForUpdate(long id) {
        return jdbc.sql(selectCoupon() + " WHERE id=:id FOR UPDATE").param("id", id)
                .query((rs, rowNum) -> coupon(rs)).optional()
                .orElseThrow(() -> unprocessable("COUPON_INVALID", "The coupon code is invalid"));
    }

    private String selectCoupon() {
        return """
                SELECT id,code,description,type,discount_value,min_order_value,max_discount_amount,
                       usage_limit_global,usage_limit_per_user,current_usage_count,is_active,is_public,
                       audience_type,first_order_only,show_on_homepage,
                       valid_from,valid_until FROM coupons
                """;
    }

    private CouponRow coupon(ResultSet rs) throws SQLException {
        return new CouponRow(rs.getLong("id"), rs.getString("code"), rs.getString("description"),
                rs.getString("type"), rs.getBigDecimal("discount_value"), rs.getLong("min_order_value"),
                nullableLong(rs, "max_discount_amount"), nullableInteger(rs, "usage_limit_global"),
                rs.getInt("usage_limit_per_user"), rs.getInt("current_usage_count"),
                rs.getBoolean("is_active"), rs.getBoolean("is_public"),
                CouponAudience.valueOf(rs.getString("audience_type")), rs.getBoolean("first_order_only"),
                rs.getBoolean("show_on_homepage"),
                rs.getTimestamp("valid_from").toInstant(),
                rs.getTimestamp("valid_until") == null ? null : rs.getTimestamp("valid_until").toInstant());
    }

    private PublicCouponResponse publicResponse(CouponRow coupon) {
        return new PublicCouponResponse(coupon.code(), coupon.description(), CouponType.valueOf(coupon.type()),
                coupon.value(), coupon.minimumOrder(), coupon.maximumDiscount(), coupon.validFrom(), coupon.validUntil());
    }

    private CustomerCouponResponse customerResponseIfEligible(CouponRow coupon, long customerId) {
        try {
            int used = usage(coupon.id(), customerId);
            int limit = customerLimit(coupon, customerId);
            if (coupon.firstOrderOnly() && !firstOrderEligible(customerId) || used >= limit) return null;
            return new CustomerCouponResponse(coupon.code(), coupon.description(), CouponType.valueOf(coupon.type()),
                    coupon.value(), coupon.minimumOrder(), coupon.maximumDiscount(), coupon.validFrom(), coupon.validUntil(),
                    coupon.audience(), coupon.firstOrderOnly(), coupon.showOnHomepage(), limit-used);
        } catch (ApiException exception) {
            return null;
        }
    }

    private int customerLimit(CouponRow coupon, long customerId) {
        if (coupon.audience() == CouponAudience.ASSIGNED_USERS) {
            Assignment assignment = jdbc.sql("""
                    SELECT usage_limit_override FROM coupon_user_assignments
                    WHERE coupon_id=:couponId AND user_id=:userId AND is_active=1
                    """).param("couponId",coupon.id()).param("userId",customerId)
                    .query((rs,rowNum)->new Assignment(nullableInteger(rs,"usage_limit_override"))).optional()
                    .orElseThrow(()->unprocessable("COUPON_NOT_ASSIGNED","This coupon is not assigned to your account"));
            return assignment.usageLimit()==null?coupon.perUserLimit():assignment.usageLimit();
        }
        return coupon.perUserLimit();
    }

    private int usage(long couponId,long customerId){return jdbc.sql("SELECT COUNT(*) FROM coupon_usage WHERE coupon_id=:couponId AND user_id=:userId")
            .param("couponId",couponId).param("userId",customerId).query(Integer.class).single();}

    private boolean firstOrderEligible(long customerId){return !jdbc.sql("""
            SELECT EXISTS(SELECT 1 FROM orders WHERE user_id=:userId
              AND status<>'CANCELLED' AND reservation_released_at IS NULL)
            """).param("userId",customerId).query(Boolean.class).single();}

    private void clear(long customerId) {
        jdbc.sql("UPDATE carts SET coupon_id=NULL,coupon_discount=0,updated_at=UTC_TIMESTAMP() WHERE user_id=:userId")
                .param("userId", customerId).update();
    }

    private static String normalize(String code) { return code.trim().toUpperCase(Locale.ROOT); }
    private static ApiException unprocessable(String code, String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }
    private static Long nullableLong(ResultSet rs, String name) throws SQLException {
        long value = rs.getLong(name); return rs.wasNull() ? null : value;
    }
    private static Integer nullableInteger(ResultSet rs, String name) throws SQLException {
        int value = rs.getInt(name); return rs.wasNull() ? null : value;
    }

    private record StoredCoupon(long couponId, long discount) {}
    private record Assignment(Integer usageLimit) {}
    private record CouponRow(long id, String code, String description, String type, BigDecimal value,
                             long minimumOrder, Long maximumDiscount, Integer globalLimit, int perUserLimit,
                             int currentUsage, boolean active, boolean publicCoupon,
                             CouponAudience audience, boolean firstOrderOnly, boolean showOnHomepage,
                             Instant validFrom, Instant validUntil) {}
}
