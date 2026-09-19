package com.moodbuds.admin;

import static com.moodbuds.admin.ProductAdminDtos.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.moodbuds.audit.AuditService;
import com.moodbuds.common.ApiException;
import com.moodbuds.common.SlugService;
import com.moodbuds.media.MediaService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsolidatedProductService {
    private final JdbcClient jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final SlugService slugs;
    private final MediaService media;
    private final AuditService audit;

    public ConsolidatedProductService(JdbcClient jdbc,NamedParameterJdbcTemplate namedJdbc,SlugService slugs,MediaService media,AuditService audit){
        this.jdbc=jdbc;this.namedJdbc=namedJdbc;this.slugs=slugs;this.media=media;this.audit=audit;
    }

    @Transactional
    public CompleteProductResponse create(CompleteProductRequest request,long adminId){
        validate(request);
        String slug=slugs.uniqueSlug("products",request.product().slug(),request.product().name(),null);
        boolean publish=request.publicationAction()==PublicationAction.PUBLISH;
        var p=productParams(request.product(),slug,adminId,publish);
        var holder=new GeneratedKeyHolder();
        namedJdbc.update("""
            INSERT INTO products(sku,name,slug,category_id,subcategory_id,gst_rate_id,description,fabric_details,color_name,price,discount_price,
              weight_grams,length_cm,width_cm,height_cm,is_featured,is_new_arrival,is_best_seller,return_window_days,is_active,publication_status,published_at,created_by,updated_by,created_at,updated_at)
            VALUES(:sku,:name,:slug,:category,:subcategory,:gst,:description,:fabric,:color,:price,:discount,:weight,:length,:width,:height,
              :featured,:newArrival,:bestSeller,:returnDays,:active,:status,:publishedAt,:admin,:admin,UTC_TIMESTAMP(),UTC_TIMESTAMP())
            """,p,holder,new String[]{"id"});
        long id=holder.getKey().longValue();
        replaceChildren(id,request,adminId,true);
        
        // Finalize temporary image files (move from temp to permanent storage)
        var mediaIds=new java.util.ArrayList<Long>();
        for(var image:request.images()) mediaIds.add(image.mediaId());
        if(request.sizeChartMediaId()!=null) mediaIds.add(request.sizeChartMediaId());
        if(!mediaIds.isEmpty()) media.finalizeTempFiles(mediaIds);
        
        var response=get(id);
        audit.record(adminId,"product.complete_created","product",id,null,response);
        return response;
    }

    @Transactional
    public CompleteProductResponse update(long id,CompleteProductRequest request,long adminId){
        var old=get(id);
        requireEditable(id);
        validate(request);
        String slug=slugs.uniqueSlug("products",request.product().slug(),request.product().name(),id);
        boolean publish=request.publicationAction()==PublicationAction.PUBLISH;
        var p=productParams(request.product(),slug,adminId,publish).addValue("id",id);
        namedJdbc.update("""
            UPDATE products SET sku=:sku,name=:name,slug=:slug,category_id=:category,subcategory_id=:subcategory,gst_rate_id=:gst,
              description=:description,fabric_details=:fabric,color_name=:color,price=:price,discount_price=:discount,weight_grams=:weight,
              length_cm=:length,width_cm=:width,height_cm=:height,is_featured=:featured,is_new_arrival=:newArrival,is_best_seller=:bestSeller,
              return_window_days=:returnDays,is_active=:active,publication_status=:status,published_at=:publishedAt,updated_by=:admin,updated_at=UTC_TIMESTAMP()
            WHERE id=:id
            """,p);
        replaceChildren(id,request,adminId,false);
        
        // Finalize temporary image files (move from temp to permanent storage)
        var mediaIds=new java.util.ArrayList<Long>();
        for(var image:request.images()) mediaIds.add(image.mediaId());
        if(request.sizeChartMediaId()!=null) mediaIds.add(request.sizeChartMediaId());
        if(!mediaIds.isEmpty()) media.finalizeTempFiles(mediaIds);
        
        var response=get(id);
        audit.record(adminId,"product.complete_updated","product",id,old,response);
        return response;
    }

    public CompleteProductResponse get(long id){
        ProductView product=jdbc.sql("SELECT * FROM products WHERE id=:id").param("id",id).query(this::productView).optional().orElseThrow(()->ApiException.notFound("Product"));
        var sizes=jdbc.sql("SELECT id,size,stock_quantity,low_stock_threshold,is_available FROM product_sizes WHERE product_id=:id ORDER BY id").param("id",id)
                .query((rs,n)->new SizeView(rs.getLong("id"),rs.getString("size"),rs.getInt("stock_quantity"),rs.getInt("low_stock_threshold"),rs.getBoolean("is_available"))).list();
        var images=jdbc.sql("SELECT id,media_asset_id,image_url,is_primary,sort_order FROM product_images WHERE product_id=:id ORDER BY is_primary DESC,sort_order,id").param("id",id)
                .query((rs,n)->new ImageView(rs.getLong("id"),rs.getLong("media_asset_id"),rs.getString("image_url"),rs.getBoolean("is_primary"),rs.getInt("sort_order"))).list();
        var moods=jdbc.sql("SELECT mood_id FROM product_mood_tags WHERE product_id=:id ORDER BY mood_id").param("id",id).query(Long.class).list();
        var chart=jdbc.sql("SELECT id,media_asset_id,chart_image_url FROM size_charts WHERE product_id=:id").param("id",id)
                .query((rs,n)->new SizeChartView(rs.getLong("id"),rs.getLong("media_asset_id"),rs.getString("chart_image_url"))).optional().orElse(null);
        return new CompleteProductResponse(product,sizes,images,moods,chart);
    }

    public com.moodbuds.common.PageResponse<ProductSummary> listSummaries(int page,int size,String query){
        int safePage=Math.max(page,0), safeSize=Math.min(Math.max(size,1),100);
        String like=(query==null||query.isBlank())?null:"%"+query.trim().toLowerCase()+"%";
        var rows=jdbc.sql("""
            SELECT p.id,p.sku,p.name,p.price,p.discount_price,p.publication_status,
                   COALESCE(SUM(ps.stock_quantity),0) AS stock,
                   COALESCE(MIN(ps.low_stock_threshold),0) AS threshold,
                   (SELECT pi.media_asset_id FROM product_images pi WHERE pi.product_id=p.id ORDER BY pi.is_primary DESC,pi.sort_order,pi.id LIMIT 1) AS primary_media_id,
                   (SELECT m.name FROM product_mood_tags pmt JOIN moods m ON m.id=pmt.mood_id WHERE pmt.product_id=p.id ORDER BY pmt.mood_id LIMIT 1) AS mood_name,
                   (SELECT m.color FROM product_mood_tags pmt JOIN moods m ON m.id=pmt.mood_id WHERE pmt.product_id=p.id ORDER BY pmt.mood_id LIMIT 1) AS mood_color
            FROM products p
            LEFT JOIN product_sizes ps ON ps.product_id=p.id AND ps.is_available=1
            WHERE p.publication_status<>'ARCHIVED' AND (:like IS NULL OR LOWER(p.name) LIKE :like OR LOWER(p.sku) LIKE :like)
            GROUP BY p.id,p.sku,p.name,p.price,p.discount_price,p.publication_status
            ORDER BY p.id DESC LIMIT :limit OFFSET :offset
            """)
            .param("like",like).param("limit",safeSize).param("offset",safePage*safeSize)
            .query((rs,n)->new ProductSummary(rs.getLong("id"),rs.getString("sku"),rs.getString("name"),rs.getLong("price"),
                    toLong(rs.getObject("discount_price")),rs.getLong("stock"),rs.getInt("threshold"),
                    toLong(rs.getObject("primary_media_id")),rs.getString("mood_name"),rs.getString("mood_color"),
                    rs.getString("publication_status"))).list();
        long total=jdbc.sql("SELECT COUNT(*) FROM products p WHERE p.publication_status<>'ARCHIVED' AND (:like IS NULL OR LOWER(p.name) LIKE :like OR LOWER(p.sku) LIKE :like)")
                .param("like",like).query(Long.class).single();
        return com.moodbuds.common.PageResponse.of(rows,safePage,safeSize,total);
    }

    private static Long toLong(Object v){ return v==null?null:((Number)v).longValue(); }

    @Transactional
    public CompleteProductResponse publish(long id,long adminId){
        requireEditable(id);
        var current=get(id);
        validateStoredForPublish(current);
        jdbc.sql("UPDATE products SET publication_status='PUBLISHED',is_active=1,published_at=COALESCE(published_at,UTC_TIMESTAMP()),updated_by=:admin,updated_at=UTC_TIMESTAMP() WHERE id=:id")
                .param("admin",adminId).param("id",id).update();
        audit.record(adminId,"product.published","product",id,current.product().publicationStatus(),PublicationStatus.PUBLISHED);
        return get(id);
    }

    @Transactional
    public CompleteProductResponse unpublish(long id,long adminId){
        var current=get(id);
        if(current.product().publicationStatus()==PublicationStatus.ARCHIVED) throw new ApiException(HttpStatus.CONFLICT,"PRODUCT_ARCHIVED","An archived product cannot be unpublished");
        jdbc.sql("UPDATE products SET publication_status='DRAFT',is_active=0,published_at=NULL,updated_by=:admin,updated_at=UTC_TIMESTAMP() WHERE id=:id")
                .param("admin",adminId).param("id",id).update();
        audit.record(adminId,"product.unpublished","product",id,current.product().publicationStatus(),PublicationStatus.DRAFT);
        return get(id);
    }

    @Transactional
    public CompleteProductResponse archive(long id,long adminId){
        var current=get(id);
        jdbc.sql("UPDATE products SET publication_status='ARCHIVED',is_active=0,updated_by=:admin,updated_at=UTC_TIMESTAMP() WHERE id=:id")
                .param("admin",adminId).param("id",id).update();
        audit.record(adminId,"product.archived","product",id,current.product().publicationStatus(),PublicationStatus.ARCHIVED);
        return get(id);
    }

    private void validate(CompleteProductRequest request){
        ProductPublicationValidator.validateCollections(request.sizes(),request.images(),request.moodIds());
        if(request.product().discountPrice()!=null&&request.product().discountPrice()>=request.product().price()) throw ApiException.badRequest("INVALID_DISCOUNT_PRICE","Discount price must be lower than regular price");
        validateReferences(request);
        if(request.publicationAction()==PublicationAction.PUBLISH) {
            ProductPublicationValidator.validatePublish(request.sizes(),request.images(),request.moodIds());
            int activeRefs=jdbc.sql("SELECT COUNT(*) FROM subcategories sc JOIN categories c ON c.id=sc.category_id AND c.is_active=1 JOIN gst_rates g ON g.id=:gst AND g.is_active=1 WHERE sc.id=:subcategory AND sc.is_active=1 AND c.id=:category")
                    .param("gst",request.product().gstRateId()).param("subcategory",request.product().subcategoryId()).param("category",request.product().categoryId()).query(Integer.class).single();
            if(activeRefs==0) throw ApiException.badRequest("INACTIVE_PRODUCT_REFERENCES","Category, subcategory, and GST rate must be active before publishing");
        }
    }

    private void validateReferences(CompleteProductRequest request){
        int relationship=jdbc.sql("SELECT COUNT(*) FROM subcategories sc JOIN categories c ON c.id=sc.category_id JOIN gst_rates g ON g.id=:gst WHERE sc.id=:subcategory AND c.id=:category")
                .param("gst",request.product().gstRateId()).param("subcategory",request.product().subcategoryId()).param("category",request.product().categoryId()).query(Integer.class).single();
        if(relationship==0) throw ApiException.badRequest("INVALID_PRODUCT_REFERENCES","Category, subcategory, or GST rate is invalid, or the subcategory does not belong to the category");
        for(long moodId:request.moodIds()) if(jdbc.sql("SELECT COUNT(*) FROM moods WHERE id=:id").param("id",moodId).query(Integer.class).single()==0) throw ApiException.badRequest("INVALID_MOOD","A selected mood does not exist");
        for(var image:request.images()) media.get(image.mediaId());
        if(request.sizeChartMediaId()!=null) media.get(request.sizeChartMediaId());
    }

    private void replaceChildren(long id,CompleteProductRequest request,long adminId,boolean creating){
        Map<String,Integer> previous=new LinkedHashMap<>();
        jdbc.sql("SELECT size,stock_quantity FROM product_sizes WHERE product_id=:id FOR UPDATE").param("id",id).query((rs,n)->Map.entry(rs.getString(1),rs.getInt(2))).list().forEach(e->previous.put(e.getKey(),e.getValue()));
        jdbc.sql("UPDATE product_sizes SET is_available=0,updated_at=UTC_TIMESTAMP() WHERE product_id=:id").param("id",id).update();
        for(var size:request.sizes()){
            int before=previous.getOrDefault(size.size(),0);
            jdbc.sql("""
                INSERT INTO product_sizes(product_id,size,stock_quantity,low_stock_threshold,is_available,created_at,updated_at)
                VALUES(:product,:size,:stock,:threshold,:available,UTC_TIMESTAMP(),UTC_TIMESTAMP())
                ON DUPLICATE KEY UPDATE stock_quantity=VALUES(stock_quantity),low_stock_threshold=VALUES(low_stock_threshold),is_available=VALUES(is_available),updated_at=UTC_TIMESTAMP()
                """).param("product",id).param("size",size.size()).param("stock",size.stockQuantity()).param("threshold",size.lowStockThreshold()).param("available",size.available()).update();
            int delta=size.stockQuantity()-before;
            if(delta!=0) jdbc.sql("INSERT INTO inventory_logs(product_id,size,change_type,quantity_change,quantity_before,quantity_after,reference_type,created_by,created_at) VALUES(:product,:size,:type,:delta,:before,:after,'product_admin',:admin,UTC_TIMESTAMP())")
                    .param("product",id).param("size",size.size()).param("type",creating?"RESTOCK":"MANUAL_ADJUSTMENT").param("delta",delta).param("before",before).param("after",size.stockQuantity()).param("admin",adminId).update();
        }
        jdbc.sql("DELETE FROM product_images WHERE product_id=:id").param("id",id).update();
        for(var image:request.images()) jdbc.sql("INSERT INTO product_images(product_id,media_asset_id,image_url,is_primary,sort_order,created_at) VALUES(:product,:media,:url,:primary,:sort,UTC_TIMESTAMP())")
                .param("product",id).param("media",image.mediaId()).param("url",media.url(image.mediaId())).param("primary",image.primary()).param("sort",image.sortOrder()).update();
        jdbc.sql("DELETE FROM product_mood_tags WHERE product_id=:id").param("id",id).update();
        for(long moodId:request.moodIds()) jdbc.sql("INSERT INTO product_mood_tags(product_id,mood_id) VALUES(:product,:mood)").param("product",id).param("mood",moodId).update();
        jdbc.sql("DELETE FROM size_charts WHERE product_id=:id").param("id",id).update();
        if(request.sizeChartMediaId()!=null) jdbc.sql("INSERT INTO size_charts(product_id,subcategory_id,media_asset_id,chart_image_url,created_at,updated_at) VALUES(:product,NULL,:media,:url,UTC_TIMESTAMP(),UTC_TIMESTAMP())")
                .param("product",id).param("media",request.sizeChartMediaId()).param("url",media.url(request.sizeChartMediaId())).update();
    }

    private void validateStoredForPublish(CompleteProductResponse p){
        ProductPublicationValidator.validatePublish(
                p.sizes().stream().map(s->new SizeInput(s.size(),s.stockQuantity(),s.lowStockThreshold(),s.available())).toList(),
                p.images().stream().map(i->new ImageInput(i.mediaId(),i.primary(),i.sortOrder())).toList(),p.moodIds());
        int activeRefs=jdbc.sql("SELECT COUNT(*) FROM products p JOIN categories c ON c.id=p.category_id AND c.is_active=1 JOIN subcategories sc ON sc.id=p.subcategory_id AND sc.category_id=c.id AND sc.is_active=1 JOIN gst_rates g ON g.id=p.gst_rate_id AND g.is_active=1 WHERE p.id=:id")
                .param("id",p.product().id()).query(Integer.class).single();
        if(activeRefs==0) throw ApiException.badRequest("INACTIVE_PRODUCT_REFERENCES","Category, subcategory, and GST rate must be active before publishing");
    }
    private void requireEditable(long id){
        String status=jdbc.sql("SELECT publication_status FROM products WHERE id=:id FOR UPDATE").param("id",id).query(String.class).optional().orElseThrow(()->ApiException.notFound("Product"));
        if("ARCHIVED".equals(status)) throw new ApiException(HttpStatus.CONFLICT,"PRODUCT_ARCHIVED","An archived product cannot be edited or published");
    }
    private MapSqlParameterSource productParams(ProductInput p,String slug,long adminId,boolean publish){
        return new MapSqlParameterSource().addValue("sku",p.sku().trim()).addValue("name",p.name().trim()).addValue("slug",slug)
                .addValue("category",p.categoryId()).addValue("subcategory",p.subcategoryId()).addValue("gst",p.gstRateId())
                .addValue("description",p.description()).addValue("fabric",p.fabricDetails()).addValue("color",p.colorName()).addValue("price",p.price())
                .addValue("discount",p.discountPrice()).addValue("weight",p.weightGrams()).addValue("length",p.lengthCm()).addValue("width",p.widthCm()).addValue("height",p.heightCm())
                .addValue("featured",p.featured()).addValue("newArrival",p.newArrival()).addValue("bestSeller",p.bestSeller()).addValue("returnDays",p.returnWindowDays())
                .addValue("active",publish).addValue("status",publish?"PUBLISHED":"DRAFT").addValue("publishedAt",publish?LocalDateTime.now(java.time.Clock.systemUTC()):null).addValue("admin",adminId);
    }
    private ProductView productView(ResultSet rs,int n)throws SQLException{
        return new ProductView(rs.getLong("id"),rs.getString("sku"),rs.getString("name"),rs.getString("slug"),rs.getLong("category_id"),rs.getLong("subcategory_id"),rs.getLong("gst_rate_id"),
                rs.getString("description"),rs.getString("fabric_details"),rs.getString("color_name"),rs.getLong("price"),nullableLong(rs,"discount_price"),nullableLong(rs,"weight_grams"),
                rs.getBigDecimal("length_cm"),rs.getBigDecimal("width_cm"),rs.getBigDecimal("height_cm"),rs.getBoolean("is_featured"),rs.getBoolean("is_new_arrival"),rs.getBoolean("is_best_seller"),rs.getInt("return_window_days"),
                PublicationStatus.valueOf(rs.getString("publication_status")),rs.getObject("published_at",LocalDateTime.class),rs.getObject("created_at",LocalDateTime.class),rs.getObject("updated_at",LocalDateTime.class));
    }
    private Long nullableLong(ResultSet rs,String column)throws SQLException{long value=rs.getLong(column);return rs.wasNull()?null:value;}
}
