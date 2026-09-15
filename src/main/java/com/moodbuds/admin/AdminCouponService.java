package com.moodbuds.admin;

import static com.moodbuds.admin.AdminCouponDtos.*;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import com.moodbuds.audit.AuditService;
import com.moodbuds.common.ApiException;
import com.moodbuds.common.PageResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminCouponService {
    private final JdbcClient jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final AuditService audit;

    public AdminCouponService(JdbcClient jdbc, NamedParameterJdbcTemplate namedJdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
        this.audit = audit;
    }

    public PageResponse<CouponResponse> list(String query, String status, int page, int size) {
        int p = Math.max(page, 0), s = Math.min(Math.max(size, 1), 100);
        String where = query == null || query.isBlank() ? "" : " WHERE (c.code LIKE :q OR c.description LIKE :q)";
        var sql = jdbc.sql(select() + where + " ORDER BY c.id DESC LIMIT :limit OFFSET :offset")
                .param("limit", s).param("offset", p * s);
        var count = jdbc.sql("SELECT COUNT(*) FROM coupons c" + where);
        if (!where.isEmpty()) {
            String q = "%" + query.trim() + "%";
            sql = sql.param("q", q); count = count.param("q", q);
        }
        List<CouponResponse> rows = sql.query((rs, rowNum) -> response(rs)).list();
        if (status != null && !status.isBlank()) {
            String wanted = status.trim().toUpperCase(Locale.ROOT);
            rows = rows.stream().filter(row -> row.status().equals(wanted)).toList();
        }
        return PageResponse.of(rows, p, s, count.query(Long.class).single());
    }

    public CouponResponse get(long id) {
        return jdbc.sql(select() + " WHERE c.id=:id").param("id", id)
                .query((rs, rowNum) -> response(rs)).optional().orElseThrow(() -> ApiException.notFound("Coupon"));
    }

    public CouponStats stats() {
        var row = jdbc.sql("""
                SELECT SUM(c.is_active=1 AND c.valid_from<=UTC_TIMESTAMP() AND
                           (c.valid_until IS NULL OR c.valid_until>=UTC_TIMESTAMP()) AND
                           (c.usage_limit_global IS NULL OR c.current_usage_count<c.usage_limit_global)) active_count,
                       (SELECT COUNT(*) FROM coupon_usage WHERE used_at>=UTC_TIMESTAMP()-INTERVAL 30 DAY) uses_30,
                       (SELECT COALESCE(SUM(discount_applied),0) FROM coupon_usage
                        WHERE used_at>=UTC_TIMESTAMP()-INTERVAL 30 DAY) discount_30,
                       (SELECT c2.code FROM coupons c2 JOIN coupon_usage cu2 ON cu2.coupon_id=c2.id
                        WHERE cu2.used_at>=UTC_TIMESTAMP()-INTERVAL 30 DAY GROUP BY c2.id,c2.code
                        ORDER BY COUNT(*) DESC,c2.id LIMIT 1) top_code
                FROM coupons c
                """).query().singleRow();
        return new CouponStats(number(row.get("active_count")), number(row.get("uses_30")),
                number(row.get("discount_30")), row.get("top_code") == null ? null : String.valueOf(row.get("top_code")));
    }

    @Transactional
    public CouponResponse create(CouponRequest request, long actorId) {
        Validated value = validate(request, 0);
        if (value.showOnHomepage()) clearHomepage(null);
        var params = parameters(value, actorId);
        var keys = new GeneratedKeyHolder();
        try {
            namedJdbc.update("""
                    INSERT INTO coupons(code,description,type,discount_value,min_order_value,max_discount_amount,
                        usage_limit_global,usage_limit_per_user,current_usage_count,is_active,is_public,audience_type,
                        first_order_only,show_on_homepage,valid_from,valid_until,created_by,created_at,updated_at)
                    VALUES(:code,:description,:type,:discountValue,:minimum,:maximum,:globalLimit,:perUser,0,:active,
                        :public,:audience,:firstOrder,:homepage,:validFrom,:validUntil,:actor,UTC_TIMESTAMP(),UTC_TIMESTAMP())
                    """, params, keys, new String[]{"id"});
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "COUPON_CODE_EXISTS", "A coupon with this code already exists");
        }
        long id = keys.getKey().longValue();
        replaceAssignments(id, request.assignedUserIds(), actorId);
        var created = get(id);
        audit.record(actorId, "coupon.created", "coupon", id, null, created);
        return created;
    }

    @Transactional
    public CouponResponse update(long id, CouponRequest request, long actorId) {
        CouponResponse old = get(id);
        Validated value = validate(request, old.currentUsageCount());
        if (old.currentUsageCount() > 0 && !old.code().equals(value.code())) {
            throw new ApiException(HttpStatus.CONFLICT, "COUPON_CODE_LOCKED", "A redeemed coupon code cannot be changed");
        }
        if (value.showOnHomepage()) clearHomepage(id);
        try {
            namedJdbc.update("""
                    UPDATE coupons SET code=:code,description=:description,type=:type,discount_value=:discountValue,
                        min_order_value=:minimum,max_discount_amount=:maximum,usage_limit_global=:globalLimit,
                        usage_limit_per_user=:perUser,is_active=:active,is_public=:public,audience_type=:audience,
                        first_order_only=:firstOrder,show_on_homepage=:homepage,valid_from=:validFrom,
                        valid_until=:validUntil,updated_at=UTC_TIMESTAMP() WHERE id=:id
                    """, parameters(value, actorId).addValue("id", id));
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "COUPON_CODE_EXISTS", "A coupon with this code already exists");
        }
        if (request.assignedUserIds() != null) replaceAssignments(id, request.assignedUserIds(), actorId);
        var updated = get(id);
        audit.record(actorId, "coupon.updated", "coupon", id, old, updated);
        return updated;
    }

    @Transactional
    public CouponResponse status(long id, boolean active, long actorId) {
        CouponResponse old = get(id);
        jdbc.sql("UPDATE coupons SET is_active=:active,updated_at=UTC_TIMESTAMP() WHERE id=:id")
                .param("active", active).param("id", id).update();
        var updated = get(id);
        audit.record(actorId, "coupon.status_updated", "coupon", id, old, updated);
        return updated;
    }

    public PageResponse<AssignmentResponse> assignments(long couponId, int page, int size) {
        get(couponId);
        int p = Math.max(page, 0), s = Math.min(Math.max(size, 1), 100);
        var rows = jdbc.sql(assignmentSelect() + " WHERE a.coupon_id=:id ORDER BY a.id DESC LIMIT :limit OFFSET :offset")
                .param("id", couponId).param("limit", s).param("offset", p * s)
                .query((rs, rowNum) -> assignment(rs)).list();
        long total = jdbc.sql("SELECT COUNT(*) FROM coupon_user_assignments WHERE coupon_id=:id")
                .param("id", couponId).query(Long.class).single();
        return PageResponse.of(rows, p, s, total);
    }

    @Transactional
    public AssignmentResponse assign(long couponId, AssignmentRequest request, long actorId) {
        CouponResponse coupon = get(couponId);
        if (coupon.audienceType() != AudienceType.ASSIGNED_USERS) {
            throw ApiException.badRequest("COUPON_NOT_ASSIGNED_AUDIENCE", "Set the coupon audience to selected customers first");
        }
        requireAssignment(request);
        jdbc.sql("""
                INSERT INTO coupon_user_assignments(coupon_id,user_id,usage_limit_override,is_active,assigned_reason,
                    refund_id,assigned_by,assigned_at,updated_at)
                VALUES(:coupon,:user,:limit,:active,:reason,:refund,:actor,UTC_TIMESTAMP(),UTC_TIMESTAMP())
                ON DUPLICATE KEY UPDATE usage_limit_override=:limit,is_active=:active,assigned_reason=:reason,
                    refund_id=:refund,assigned_by=:actor,updated_at=UTC_TIMESTAMP()
                """).param("coupon", couponId).param("user", request.userId()).param("limit", request.usageLimitOverride(), Types.INTEGER)
                .param("active", request.active()).param("reason", blankToNull(request.assignedReason()), Types.VARCHAR)
                .param("refund", request.refundId(), Types.BIGINT).param("actor", actorId).update();
        AssignmentResponse result = assignment(couponId, request.userId());
        audit.record(actorId, "coupon.customer_assigned", "coupon_assignment", result.id(), null, result);
        return result;
    }

    @Transactional
    public void revoke(long couponId, long userId, long actorId) {
        AssignmentResponse old = assignment(couponId, userId);
        jdbc.sql("UPDATE coupon_user_assignments SET is_active=0,updated_at=UTC_TIMESTAMP() WHERE coupon_id=:coupon AND user_id=:user")
                .param("coupon", couponId).param("user", userId).update();
        audit.record(actorId, "coupon.customer_revoked", "coupon_assignment", old.id(), old, null);
    }

    public PageResponse<java.util.Map<String,Object>> usage(long couponId, int page, int size) {
        get(couponId);
        int p=Math.max(page,0), s=Math.min(Math.max(size,1),100);
        var rows=jdbc.sql("""
                SELECT cu.id,cu.user_id,u.email customer_email,CONCAT_WS(' ',u.first_name,u.last_name) customer_name,
                       cu.order_id,o.order_number,cu.discount_applied,cu.used_at
                FROM coupon_usage cu JOIN users u ON u.id=cu.user_id LEFT JOIN orders o ON o.id=cu.order_id
                WHERE cu.coupon_id=:id ORDER BY cu.id DESC LIMIT :limit OFFSET :offset
                """).param("id",couponId).param("limit",s).param("offset",p*s).query().listOfRows();
        long total=jdbc.sql("SELECT COUNT(*) FROM coupon_usage WHERE coupon_id=:id").param("id",couponId).query(Long.class).single();
        return PageResponse.of(rows,p,s,total);
    }

    private Validated validate(CouponRequest request, int currentUsage) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z0-9][A-Z0-9_-]{2,49}")) throw ApiException.badRequest("INVALID_COUPON_CODE", "Use 3-50 letters, numbers, hyphens or underscores");
        if (request.minOrderValue() < 0 || request.maxDiscountAmount() != null && request.maxDiscountAmount() <= 0)
            throw ApiException.badRequest("INVALID_COUPON_AMOUNT", "Coupon amounts must be positive");
        if (request.type() == CouponType.PERCENTAGE && request.discountValue().compareTo(BigDecimal.valueOf(100)) > 0)
            throw ApiException.badRequest("INVALID_COUPON_PERCENTAGE", "Percentage discount cannot exceed 100");
        Long maximum = request.type() == CouponType.FLAT ? null : request.maxDiscountAmount();
        int perUser = request.firstOrderOnly() ? 1 : request.usageLimitPerUser();
        if (perUser < 1) throw ApiException.badRequest("INVALID_USER_LIMIT", "Per-customer usage limit must be at least one");
        if (request.usageLimitGlobal() != null && (request.usageLimitGlobal() < 1 || request.usageLimitGlobal() < currentUsage))
            throw ApiException.badRequest("INVALID_GLOBAL_LIMIT", "Global limit cannot be below one or completed redemptions");
        if (request.validUntil() != null && !request.validUntil().isAfter(request.validFrom()))
            throw ApiException.badRequest("INVALID_COUPON_DATES", "Expiry must be after the start date");
        if (request.showOnHomepage() && (!request.firstOrderOnly() || request.audienceType() != AudienceType.PUBLIC))
            throw ApiException.badRequest("INVALID_HOMEPAGE_COUPON", "Homepage coupons must be public first-order coupons");
        return new Validated(code, blankToNull(request.description()), request.type(), request.discountValue(),
                request.minOrderValue(), maximum, request.usageLimitGlobal(), perUser, request.audienceType(),
                request.firstOrderOnly(), request.showOnHomepage(), request.active(), request.validFrom(), request.validUntil());
    }

    private MapSqlParameterSource parameters(Validated v, long actorId) {
        return new MapSqlParameterSource().addValue("code",v.code()).addValue("description",v.description())
                .addValue("type",v.type().name()).addValue("discountValue",v.discountValue())
                .addValue("minimum",v.minimum()).addValue("maximum",v.maximum(),Types.INTEGER)
                .addValue("globalLimit",v.globalLimit(),Types.INTEGER).addValue("perUser",v.perUser())
                .addValue("active",v.active()).addValue("public",v.audience()==AudienceType.PUBLIC)
                .addValue("audience",v.audience().name()).addValue("firstOrder",v.firstOrder())
                .addValue("homepage",v.showOnHomepage()).addValue("validFrom",v.validFrom())
                .addValue("validUntil",v.validUntil(),Types.TIMESTAMP).addValue("actor",actorId);
    }

    private void replaceAssignments(long couponId, List<Long> users, long actorId) {
        if (users == null) return;
        jdbc.sql("UPDATE coupon_user_assignments SET is_active=0,updated_at=UTC_TIMESTAMP() WHERE coupon_id=:id")
                .param("id", couponId).update();
        for (Long userId : users.stream().filter(java.util.Objects::nonNull).distinct().toList()) {
            if (jdbc.sql("SELECT COUNT(*) FROM users WHERE id=:id AND is_active=1").param("id",userId).query(Integer.class).single()==0)
                throw ApiException.notFound("Customer");
            jdbc.sql("""
                    INSERT INTO coupon_user_assignments(coupon_id,user_id,usage_limit_override,is_active,
                        assigned_reason,refund_id,assigned_by,assigned_at,updated_at)
                    VALUES(:coupon,:user,NULL,1,NULL,NULL,:actor,UTC_TIMESTAMP(),UTC_TIMESTAMP())
                    ON DUPLICATE KEY UPDATE is_active=1,assigned_by=:actor,updated_at=UTC_TIMESTAMP()
                    """).param("coupon",couponId).param("user",userId).param("actor",actorId).update();
        }
    }

    private void requireAssignment(AssignmentRequest request) {
        if (request.usageLimitOverride() != null && request.usageLimitOverride() < 1)
            throw ApiException.badRequest("INVALID_USER_LIMIT", "Assignment usage limit must be at least one");
        if (jdbc.sql("SELECT COUNT(*) FROM users WHERE id=:id AND is_active=1").param("id",request.userId()).query(Integer.class).single()==0)
            throw ApiException.notFound("Customer");
        if (request.refundId()!=null && jdbc.sql("SELECT COUNT(*) FROM refunds r JOIN orders o ON o.id=r.order_id WHERE r.id=:id AND o.user_id=:user")
                .param("id",request.refundId()).param("user",request.userId()).query(Integer.class).single()==0)
            throw ApiException.badRequest("REFUND_CUSTOMER_MISMATCH", "The refund does not belong to this customer");
    }

    private void clearHomepage(Long exceptId) {
        String clause = exceptId == null ? "" : " AND id<>:id";
        var query = jdbc.sql("UPDATE coupons SET show_on_homepage=0,updated_at=UTC_TIMESTAMP() WHERE show_on_homepage=1" + clause);
        if (exceptId != null) query = query.param("id", exceptId);
        query.update();
    }

    private AssignmentResponse assignment(long couponId, long userId) {
        return jdbc.sql(assignmentSelect()+" WHERE a.coupon_id=:coupon AND a.user_id=:user")
                .param("coupon",couponId).param("user",userId).query((rs,n)->assignment(rs)).optional()
                .orElseThrow(()->ApiException.notFound("Coupon assignment"));
    }

    private String select() { return """
            SELECT c.*,
              (SELECT COUNT(*) FROM coupon_user_assignments a WHERE a.coupon_id=c.id AND a.is_active=1) assigned_count,
              (SELECT COALESCE(SUM(discount_applied),0) FROM coupon_usage cu WHERE cu.coupon_id=c.id) total_discount
            FROM coupons c
            """; }

    private CouponResponse response(ResultSet rs) throws SQLException {
        Instant now=Instant.now(), from=rs.getTimestamp("valid_from").toInstant();
        var until=rs.getTimestamp("valid_until"); Instant end=until==null?null:until.toInstant();
        boolean active=rs.getBoolean("is_active"); Integer global=nullableInt(rs,"usage_limit_global"); int used=rs.getInt("current_usage_count");
        String status=!active?"INACTIVE":now.isBefore(from)?"SCHEDULED":end!=null&&now.isAfter(end)?"EXPIRED":global!=null&&used>=global?"EXHAUSTED":"ACTIVE";
        return new CouponResponse(rs.getLong("id"),rs.getString("code"),rs.getString("description"),CouponType.valueOf(rs.getString("type")),
                rs.getBigDecimal("discount_value"),rs.getLong("min_order_value"),nullableLong(rs,"max_discount_amount"),global,
                rs.getInt("usage_limit_per_user"),used,AudienceType.valueOf(rs.getString("audience_type")),
                rs.getBoolean("first_order_only"),rs.getBoolean("show_on_homepage"),active,status,from,end,
                rs.getInt("assigned_count"),rs.getLong("total_discount"),rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("updated_at").toInstant());
    }

    private String assignmentSelect(){return """
            SELECT a.*,u.email,CONCAT_WS(' ',u.first_name,u.last_name) customer_name,
              (SELECT COUNT(*) FROM coupon_usage cu WHERE cu.coupon_id=a.coupon_id AND cu.user_id=a.user_id) usage_count
            FROM coupon_user_assignments a JOIN users u ON u.id=a.user_id
            """;}
    private AssignmentResponse assignment(ResultSet rs)throws SQLException{return new AssignmentResponse(rs.getLong("id"),rs.getLong("coupon_id"),rs.getLong("user_id"),rs.getString("email"),rs.getString("customer_name"),nullableInt(rs,"usage_limit_override"),rs.getInt("usage_count"),rs.getBoolean("is_active"),rs.getString("assigned_reason"),nullableLong(rs,"refund_id"),rs.getLong("assigned_by"),rs.getTimestamp("assigned_at").toInstant(),rs.getTimestamp("updated_at").toInstant());}
    private static Integer nullableInt(ResultSet rs,String name)throws SQLException{int value=rs.getInt(name);return rs.wasNull()?null:value;}
    private static Long nullableLong(ResultSet rs,String name)throws SQLException{long value=rs.getLong(name);return rs.wasNull()?null:value;}
    private static long number(Object value){return value==null?0:((Number)value).longValue();}
    private static String blankToNull(String value){return value==null||value.isBlank()?null:value.trim();}
    private record Validated(String code,String description,CouponType type,BigDecimal discountValue,long minimum,Long maximum,Integer globalLimit,int perUser,AudienceType audience,boolean firstOrder,boolean showOnHomepage,boolean active,Instant validFrom,Instant validUntil){}
}
