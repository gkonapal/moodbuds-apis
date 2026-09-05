package com.moodbuds.admin;

import java.util.List;
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
@RequestMapping("/api/v1/admin/products")
public class ProductOperationsController {
    private final JdbcClient jdbc;
    private final AdminCrudService crud;
    private final AuditService audit;
    public ProductOperationsController(JdbcClient jdbc,AdminCrudService crud,AuditService audit){this.jdbc=jdbc;this.crud=crud;this.audit=audit;}

    @GetMapping("/{id}/full")
    @PreAuthorize("hasAuthority('catalog.read') or hasRole('SUPER_ADMIN')")
    Map<String,Object> full(@PathVariable long id){
        var result=new java.util.LinkedHashMap<>(crud.get("products",id));
        result.put("sizes",jdbc.sql("SELECT * FROM product_sizes WHERE product_id=:id ORDER BY id").param("id",id).query().listOfRows());
        result.put("images",jdbc.sql("SELECT * FROM product_images WHERE product_id=:id ORDER BY is_primary DESC,id").param("id",id).query().listOfRows());
        result.put("moodIds",jdbc.sql("SELECT mood_id FROM product_mood_tags WHERE product_id=:id ORDER BY mood_id").param("id",id).query(Long.class).list());
        result.put("sizeCharts",jdbc.sql("SELECT * FROM size_charts WHERE product_id=:id OR product_id IS NULL AND subcategory_id=:subcategoryId ORDER BY product_id DESC")
                .param("id",id).param("subcategoryId",result.get("subcategory_id")).query().listOfRows());
        return result;
    }

    @PutMapping("/{id}/sizes")
    @PreAuthorize("hasAuthority('catalog.manage') or hasRole('SUPER_ADMIN')")
    @Transactional
    List<Map<String,Object>> sizes(@PathVariable long id,@RequestBody List<SizeRequest> sizes,@AuthenticationPrincipal Jwt jwt){
        crud.get("products",id);
        for(var size:sizes){
            if(size.stockQuantity()<0) throw ApiException.badRequest("INVALID_STOCK","Stock cannot be negative");
            jdbc.sql("""
              INSERT INTO product_sizes(product_id,size,stock_quantity,low_stock_threshold,is_available,created_at,updated_at)
              VALUES(:product,:size,:stock,:threshold,:available,UTC_TIMESTAMP(),UTC_TIMESTAMP())
              ON DUPLICATE KEY UPDATE stock_quantity=VALUES(stock_quantity),low_stock_threshold=VALUES(low_stock_threshold),is_available=VALUES(is_available),updated_at=UTC_TIMESTAMP()
              """).param("product",id).param("size",size.size()).param("stock",size.stockQuantity())
                    .param("threshold",size.lowStockThreshold()).param("available",size.available()).update();
        }
        audit.record(CurrentAdmin.id(jwt),"product.sizes_updated","product",id,null,sizes);
        return jdbc.sql("SELECT * FROM product_sizes WHERE product_id=:id ORDER BY id").param("id",id).query().listOfRows();
    }

    @PostMapping("/{id}/images")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('catalog.manage') or hasRole('SUPER_ADMIN')")
    @Transactional
    List<Map<String,Object>> addImage(@PathVariable long id,@RequestBody ImageRequest image,@AuthenticationPrincipal Jwt jwt){
        crud.get("products",id);
        if(image.primary()) jdbc.sql("UPDATE product_images SET is_primary=0 WHERE product_id=:id").param("id",id).update();
        jdbc.sql("INSERT INTO product_images(product_id,image_url,is_primary,created_at) VALUES(:id,:url,:primary,UTC_TIMESTAMP())")
                .param("id",id).param("url",image.imageUrl()).param("primary",image.primary()).update();
        audit.record(CurrentAdmin.id(jwt),"product.image_added","product",id,null,image);
        return jdbc.sql("SELECT * FROM product_images WHERE product_id=:id ORDER BY is_primary DESC,id").param("id",id).query().listOfRows();
    }

    @DeleteMapping("/{productId}/images/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('catalog.manage') or hasRole('SUPER_ADMIN')")
    void deleteImage(@PathVariable long productId,@PathVariable long imageId,@AuthenticationPrincipal Jwt jwt){
        if(jdbc.sql("DELETE FROM product_images WHERE id=:image AND product_id=:product").param("image",imageId).param("product",productId).update()==0) throw ApiException.notFound("Product image");
        audit.record(CurrentAdmin.id(jwt),"product.image_deleted","product",productId,Map.of("imageId",imageId),null);
    }

    @PutMapping("/{id}/moods")
    @PreAuthorize("hasAuthority('moods.manage') or hasRole('SUPER_ADMIN')")
    @Transactional
    List<Long> moods(@PathVariable long id,@RequestBody MoodAssignment body,@AuthenticationPrincipal Jwt jwt){
        crud.get("products",id);
        if(body.moodIds().isEmpty()) throw ApiException.badRequest("MOOD_REQUIRED","A product must have at least one mood");
        jdbc.sql("DELETE FROM product_mood_tags WHERE product_id=:id").param("id",id).update();
        for(Long moodId:new java.util.LinkedHashSet<>(body.moodIds())) jdbc.sql("INSERT INTO product_mood_tags(product_id,mood_id) VALUES(:product,:mood)").param("product",id).param("mood",moodId).update();
        audit.record(CurrentAdmin.id(jwt),"product.moods_updated","product",id,null,body);
        return body.moodIds();
    }

    public record SizeRequest(String size,int stockQuantity,int lowStockThreshold,boolean available){}
    public record ImageRequest(String imageUrl,boolean primary){}
    public record MoodAssignment(List<Long> moodIds){public MoodAssignment{moodIds=moodIds==null?List.of():List.copyOf(moodIds);}}
}
