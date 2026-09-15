package com.moodbuds.admin;

import java.util.Map;

import com.moodbuds.audit.AuditService;
import com.moodbuds.auth.CurrentAdmin;
import com.moodbuds.common.ApiException;
import com.moodbuds.common.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/inventory")
public class InventoryAdminController {
    private final JdbcClient jdbc; private final AuditService audit; private final InventoryOverviewService overviewService;
    public InventoryAdminController(JdbcClient jdbc,AuditService audit,InventoryOverviewService overviewService){this.jdbc=jdbc;this.audit=audit;this.overviewService=overviewService;}

    @GetMapping("/overview") @PreAuthorize("hasAuthority('inventory.read') or hasRole('SUPER_ADMIN')")
    Object overview(){return overviewService.overview();}

    @GetMapping("/logs") @PreAuthorize("hasAuthority('inventory.read') or hasRole('SUPER_ADMIN')")
    Object logs(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size){
        int s=Math.min(Math.max(size,1),100),p=Math.max(page,0);
        var rows=jdbc.sql("SELECT il.*,p.name AS product_name,p.sku FROM inventory_logs il JOIN products p ON p.id=il.product_id ORDER BY il.id DESC LIMIT :limit OFFSET :offset").param("limit",s).param("offset",p*s).query().listOfRows();
        long total=jdbc.sql("SELECT COUNT(*) FROM inventory_logs").query(Long.class).single();return PageResponse.of(rows,p,s,total);
    }

    @GetMapping("/low-stock") @PreAuthorize("hasAuthority('inventory.read') or hasRole('SUPER_ADMIN')")
    Object lowStock(){return jdbc.sql("SELECT ps.*,p.name AS product_name,p.sku FROM product_sizes ps JOIN products p ON p.id=ps.product_id WHERE ps.stock_quantity<=ps.low_stock_threshold ORDER BY ps.stock_quantity,p.name").query().listOfRows();}

    @PostMapping("/adjustments") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('inventory.manage') or hasRole('SUPER_ADMIN')") @Transactional
    Object adjust(@RequestBody Adjustment request,@AuthenticationPrincipal Jwt jwt){
        var row=jdbc.sql("SELECT stock_quantity FROM product_sizes WHERE product_id=:product AND size=:size FOR UPDATE").param("product",request.productId()).param("size",request.size()).query(Integer.class).optional().orElseThrow(()->ApiException.notFound("Product size"));
        int after=row+request.quantityChange();if(after<0)throw ApiException.badRequest("INSUFFICIENT_STOCK","The adjustment would make stock negative");
        jdbc.sql("UPDATE product_sizes SET stock_quantity=:after,is_available=:available,updated_at=UTC_TIMESTAMP() WHERE product_id=:product AND size=:size").param("after",after).param("available",after>0).param("product",request.productId()).param("size",request.size()).update();
        jdbc.sql("INSERT INTO inventory_logs(product_id,size,change_type,quantity_change,quantity_before,quantity_after,reference_type,created_by,created_at) VALUES(:product,:size,'MANUAL_ADJUSTMENT',:change,:before,:after,:reference,:admin,UTC_TIMESTAMP())")
                .param("product",request.productId()).param("size",request.size()).param("change",request.quantityChange()).param("before",row).param("after",after).param("reference",request.reason()).param("admin",CurrentAdmin.id(jwt)).update();
        var result=Map.of("productId",request.productId(),"size",request.size(),"quantityBefore",row,"quantityAfter",after);
        audit.record(CurrentAdmin.id(jwt),"inventory.adjusted","product",request.productId(),Map.of("stock",row),result);return result;
    }
    public record Adjustment(long productId,String size,int quantityChange,String reason){}
}
