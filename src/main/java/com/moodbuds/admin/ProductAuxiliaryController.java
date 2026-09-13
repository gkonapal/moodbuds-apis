package com.moodbuds.admin;

import java.util.Map;

import com.moodbuds.audit.AuditService;
import com.moodbuds.auth.CurrentAdmin;
import com.moodbuds.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class ProductAuxiliaryController {
    private final JdbcClient jdbc;
    private final AuditService audit;
    public ProductAuxiliaryController(JdbcClient jdbc, AuditService audit) { this.jdbc=jdbc; this.audit=audit; }

    @PutMapping("/products/{productId}/size-chart")
    @PreAuthorize("hasAuthority('catalog.manage') or hasRole('SUPER_ADMIN')")
    @Transactional
    Object productChart(@PathVariable long productId, @RequestBody ChartRequest body, @AuthenticationPrincipal Jwt jwt) {
        String status=jdbc.sql("SELECT publication_status FROM products WHERE id=:id").param("id",productId).query(String.class).optional().orElseThrow(()->ApiException.notFound("Product"));
        if("ARCHIVED".equals(status)) throw new ApiException(HttpStatus.CONFLICT,"PRODUCT_ARCHIVED","An archived product cannot be edited");
        jdbc.sql("""
          INSERT INTO size_charts(product_id,subcategory_id,chart_image_url,created_at,updated_at)
          VALUES(:product,NULL,:url,UTC_TIMESTAMP(),UTC_TIMESTAMP())
          ON DUPLICATE KEY UPDATE chart_image_url=:url,updated_at=UTC_TIMESTAMP()
          """).param("product",productId).param("url",body.chartImageUrl()).update();
        audit.record(CurrentAdmin.id(jwt),"product.size_chart_updated","product",productId,null,body);
        return jdbc.sql("SELECT * FROM size_charts WHERE product_id=:id").param("id",productId).query().singleRow();
    }

    @PutMapping("/subcategories/{subcategoryId}/size-chart")
    @PreAuthorize("hasAuthority('catalog.manage') or hasRole('SUPER_ADMIN')")
    @Transactional
    Object subcategoryChart(@PathVariable long subcategoryId, @RequestBody ChartRequest body, @AuthenticationPrincipal Jwt jwt) {
        if (jdbc.sql("SELECT COUNT(*) FROM subcategories WHERE id=:id").param("id",subcategoryId).query(Integer.class).single()==0) throw ApiException.notFound("Subcategory");
        jdbc.sql("""
          INSERT INTO size_charts(product_id,subcategory_id,chart_image_url,created_at,updated_at)
          VALUES(NULL,:subcategory,:url,UTC_TIMESTAMP(),UTC_TIMESTAMP())
          ON DUPLICATE KEY UPDATE chart_image_url=:url,updated_at=UTC_TIMESTAMP()
          """).param("subcategory",subcategoryId).param("url",body.chartImageUrl()).update();
        audit.record(CurrentAdmin.id(jwt),"subcategory.size_chart_updated","subcategory",subcategoryId,null,body);
        return jdbc.sql("SELECT * FROM size_charts WHERE subcategory_id=:id AND product_id IS NULL").param("id",subcategoryId).query().singleRow();
    }

    @DeleteMapping("/size-charts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('catalog.manage') or hasRole('SUPER_ADMIN')")
    void deleteChart(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        if (jdbc.sql("DELETE FROM size_charts WHERE id=:id").param("id",id).update()==0) throw ApiException.notFound("Size chart");
        audit.record(CurrentAdmin.id(jwt),"size_chart.deleted","size_chart",id,null,null);
    }

    @GetMapping("/coupons/{couponId}/usage")
    @PreAuthorize("hasAuthority('coupons.manage') or hasRole('SUPER_ADMIN')")
    Object couponUsage(@PathVariable long couponId) {
        return jdbc.sql("SELECT cu.*,o.order_number,u.email customer_email FROM coupon_usage cu LEFT JOIN orders o ON o.id=cu.order_id JOIN users u ON u.id=cu.user_id WHERE cu.coupon_id=:id ORDER BY cu.id DESC")
                .param("id",couponId).query().listOfRows();
    }

    public record ChartRequest(String chartImageUrl) {}
}
