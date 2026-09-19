package com.moodbuds.catalog;

import static com.moodbuds.catalog.api.CatalogDtos.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.moodbuds.common.ApiException;
import com.moodbuds.common.PageResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class CatalogService {
    private static final String EFFECTIVE_PRICE =
            "CASE WHEN p.discount_price IS NOT NULL AND p.discount_price<p.price THEN p.discount_price ELSE p.price END";
    private static final String PRODUCT_FROM = """
            FROM products p
            JOIN categories c ON c.id=p.category_id AND c.is_active=1
            JOIN subcategories sc ON sc.id=p.subcategory_id AND sc.is_active=1
            WHERE p.is_active=1 AND p.publication_status='PUBLISHED'
            """;

    private final JdbcClient jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    public CatalogService(JdbcClient jdbc, NamedParameterJdbcTemplate namedJdbc) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
    }

    public HomeResponse home(int collectionSize) {
        int limit = Math.min(Math.max(collectionSize, 1), 24);
        return new HomeResponse(moods(), categories(),
                collection("featured", limit), collection("newArrival", limit), collection("bestSeller", limit));
    }

    public List<CategorySummary> categories() {
        return jdbc.sql("""
                SELECT c.id,c.name,c.slug,
                       COUNT(DISTINCT CASE WHEN sc.is_active=1 THEN sc.id END) subcategory_count,
                       COUNT(DISTINCT CASE WHEN p.is_active=1 AND p.publication_status='PUBLISHED' AND sc.is_active=1 THEN p.id END) product_count
                FROM categories c
                LEFT JOIN subcategories sc ON sc.category_id=c.id
                LEFT JOIN products p ON p.category_id=c.id AND p.subcategory_id=sc.id
                WHERE c.is_active=1
                GROUP BY c.id,c.name,c.slug
                ORDER BY c.name,c.id
                """).query((rs, rowNum) -> category(rs)).list();
    }

    public CategoryDetail category(String slug) {
        var category = jdbc.sql("""
                SELECT c.id,c.name,c.slug,
                       COUNT(DISTINCT CASE WHEN p.is_active=1 AND p.publication_status='PUBLISHED' AND sc.is_active=1 THEN p.id END) product_count
                FROM categories c
                LEFT JOIN subcategories sc ON sc.category_id=c.id
                LEFT JOIN products p ON p.category_id=c.id AND p.subcategory_id=sc.id
                WHERE c.slug=:slug AND c.is_active=1
                GROUP BY c.id,c.name,c.slug
                """).param("slug", slug).query((rs, rowNum) -> new CategoryDetail(
                        rs.getLong("id"), rs.getString("name"), rs.getString("slug"),
                        rs.getLong("product_count"), List.of())).optional()
                .orElseThrow(() -> ApiException.notFound("Category"));
        return new CategoryDetail(category.id(), category.name(), category.slug(), category.productCount(),
                subcategories(category.id()));
    }

    public List<SubcategorySummary> subcategories(String categorySlug) {
        return subcategories(category(categorySlug).id());
    }

    public SubcategorySummary subcategory(String slug) {
        return jdbc.sql("""
                SELECT sc.id,sc.name,sc.slug,c.id category_id,c.name category_name,c.slug category_slug,
                       COUNT(DISTINCT CASE WHEN p.is_active=1 AND p.publication_status='PUBLISHED' THEN p.id END) product_count
                FROM subcategories sc
                JOIN categories c ON c.id=sc.category_id AND c.is_active=1
                LEFT JOIN products p ON p.subcategory_id=sc.id
                WHERE sc.slug=:slug AND sc.is_active=1
                GROUP BY sc.id,sc.name,sc.slug,c.id,c.name,c.slug
                """).param("slug", slug).query((rs, rowNum) -> subcategory(rs)).optional()
                .orElseThrow(() -> ApiException.notFound("Subcategory"));
    }

    public List<MoodSummary> moods() {
        return jdbc.sql("""
                SELECT m.id,m.name,m.slug,m.tagline,m.personality_tagline,m.banner_image_url,m.color,
                       COUNT(DISTINCT CASE WHEN p.is_active=1 AND p.publication_status='PUBLISHED' AND c.is_active=1 AND sc.is_active=1 THEN p.id END) product_count
                FROM moods m
                LEFT JOIN product_mood_tags pmt ON pmt.mood_id=m.id
                LEFT JOIN products p ON p.id=pmt.product_id
                LEFT JOIN categories c ON c.id=p.category_id
                LEFT JOIN subcategories sc ON sc.id=p.subcategory_id
                WHERE m.is_active=1
                GROUP BY m.id,m.name,m.slug,m.tagline,m.personality_tagline,m.banner_image_url,m.color
                ORDER BY m.name,m.id
                """).query((rs, rowNum) -> mood(rs)).list();
    }

    public MoodSummary mood(String slug) {
        return moods().stream().filter(mood -> mood.slug().equals(slug)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Mood"));
    }

    public PageResponse<ProductCard> products(ProductSearchCriteria criteria) {
        var where = new StringBuilder(PRODUCT_FROM);
        var params = new MapSqlParameterSource();
        addFilters(criteria, where, params);
        long total = namedJdbc.queryForObject("SELECT COUNT(DISTINCT p.id) " + where, params, Long.class);
        params.addValue("limit", criteria.pageSize()).addValue("offset", criteria.page() * criteria.pageSize());
        String sql = productSelect() + where + orderBy(criteria.sort()) + " LIMIT :limit OFFSET :offset";
        List<ProductCard> content = namedJdbc.query(sql, params, (rs, rowNum) -> productCard(rs));
        return PageResponse.of(content, criteria.page(), criteria.pageSize(), total);
    }

    public ProductDetail product(String slug) {
        var base = jdbc.sql("""
                SELECT p.id,p.sku,p.slug,p.name,p.description,p.fabric_details,p.color_name,
                       p.price,p.discount_price,p.is_featured,p.is_new_arrival,p.is_best_seller,
                       p.return_window_days,c.id category_id,c.name category_name,c.slug category_slug,
                       sc.id subcategory_id,sc.name subcategory_name,sc.slug subcategory_slug,
                       g.name gst_name,g.hsn_code,g.rate_percentage,
                       EXISTS(SELECT 1 FROM product_sizes ps WHERE ps.product_id=p.id
                              AND ps.is_available=1 AND ps.stock_quantity>0) in_stock
                FROM products p
                JOIN categories c ON c.id=p.category_id AND c.is_active=1
                JOIN subcategories sc ON sc.id=p.subcategory_id AND sc.is_active=1
                LEFT JOIN gst_rates g ON g.id=p.gst_rate_id AND g.is_active=1
                WHERE p.slug=:slug AND p.is_active=1 AND p.publication_status='PUBLISHED'
                """).param("slug", slug).query((rs, rowNum) -> productBase(rs)).optional()
                .orElseThrow(() -> ApiException.notFound("Product"));
        long id = base.id();
        var images = jdbc.sql("""
                SELECT id,image_url,is_primary FROM product_images
                WHERE product_id=:id ORDER BY is_primary DESC,id
                """).param("id", id).query((rs, rowNum) -> new ProductImage(
                        rs.getLong("id"), rs.getString("image_url"), rs.getBoolean("is_primary"))).list();
        var sizes = sizes(id);
        var moods = jdbc.sql("""
                SELECT m.id,m.name,m.slug,m.color FROM moods m
                JOIN product_mood_tags pmt ON pmt.mood_id=m.id
                WHERE pmt.product_id=:id AND m.is_active=1 ORDER BY m.name,m.id
                """).param("id", id).query((rs, rowNum) -> new MoodRef(
                        rs.getLong("id"), rs.getString("name"), rs.getString("slug"), rs.getString("color"))).list();
        var chart = jdbc.sql("""
                SELECT chart_image_url,
                       CASE WHEN product_id IS NOT NULL THEN 'PRODUCT' ELSE 'SUBCATEGORY' END chart_source
                FROM size_charts
                WHERE product_id=:productId OR (product_id IS NULL AND subcategory_id=:subcategoryId)
                ORDER BY (product_id IS NOT NULL) DESC,id DESC LIMIT 1
                """).param("productId", id).param("subcategoryId", base.subcategory().id())
                .query((rs, rowNum) -> new SizeChart(rs.getString("chart_source"), rs.getString("chart_image_url")))
                .optional().orElse(null);
        return base.detail(images, sizes, moods, chart);
    }

    public ProductAvailability availability(String slug) {
        var product = productIdentity(slug);
        var sizes = sizes(product.id());
        return new ProductAvailability(product.slug(), sizes.stream().anyMatch(ProductSize::inStock), sizes);
    }

    public SizeChart sizeChart(String slug) {
        var detail = product(slug);
        if (detail.sizeChart() == null) throw ApiException.notFound("Size chart");
        return detail.sizeChart();
    }

    private List<ProductCard> collection(String flag, int size) {
        return products(new ProductSearchCriteria(null, null, null, null, null, null,
                null, null, true, flag.equals("featured") ? true : null,
                flag.equals("newArrival") ? true : null, flag.equals("bestSeller") ? true : null,
                "featured", 0, size)).content();
    }

    private List<SubcategorySummary> subcategories(long categoryId) {
        return jdbc.sql("""
                SELECT sc.id,sc.name,sc.slug,c.id category_id,c.name category_name,c.slug category_slug,
                       COUNT(DISTINCT CASE WHEN p.is_active=1 AND p.publication_status='PUBLISHED' THEN p.id END) product_count
                FROM subcategories sc
                JOIN categories c ON c.id=sc.category_id AND c.is_active=1
                LEFT JOIN products p ON p.subcategory_id=sc.id
                WHERE sc.category_id=:categoryId AND sc.is_active=1
                GROUP BY sc.id,sc.name,sc.slug,c.id,c.name,c.slug
                ORDER BY sc.name,sc.id
                """).param("categoryId", categoryId).query((rs, rowNum) -> subcategory(rs)).list();
    }

    private void addFilters(ProductSearchCriteria c, StringBuilder sql, MapSqlParameterSource params) {
        if (c.query() != null) {
            sql.append(" AND (p.name LIKE :query OR p.sku LIKE :query OR p.description LIKE :query)");
            params.addValue("query", "%" + c.query() + "%");
        }
        if (c.category() != null) { sql.append(" AND c.slug=:category"); params.addValue("category", c.category()); }
        if (c.subcategory() != null) { sql.append(" AND sc.slug=:subcategory"); params.addValue("subcategory", c.subcategory()); }
        if (c.mood() != null) {
            sql.append(" AND EXISTS(SELECT 1 FROM product_mood_tags pmt JOIN moods m ON m.id=pmt.mood_id AND m.is_active=1 WHERE pmt.product_id=p.id AND m.slug=:mood)");
            params.addValue("mood", c.mood());
        }
        if (c.productSize() != null) {
            sql.append(" AND EXISTS(SELECT 1 FROM product_sizes ps WHERE ps.product_id=p.id AND ps.size=:productSize AND ps.is_available=1 AND ps.stock_quantity>0)");
            params.addValue("productSize", c.productSize());
        }
        if (c.color() != null) { sql.append(" AND LOWER(p.color_name)=LOWER(:color)"); params.addValue("color", c.color()); }
        if (c.minPrice() != null) { sql.append(" AND ").append(EFFECTIVE_PRICE).append(">=:minPrice"); params.addValue("minPrice", c.minPrice()); }
        if (c.maxPrice() != null) { sql.append(" AND ").append(EFFECTIVE_PRICE).append("<=:maxPrice"); params.addValue("maxPrice", c.maxPrice()); }
        if (c.inStock() != null) sql.append(c.inStock()
                ? " AND EXISTS(SELECT 1 FROM product_sizes ps WHERE ps.product_id=p.id AND ps.is_available=1 AND ps.stock_quantity>0)"
                : " AND NOT EXISTS(SELECT 1 FROM product_sizes ps WHERE ps.product_id=p.id AND ps.is_available=1 AND ps.stock_quantity>0)");
        if (c.featured() != null) { sql.append(" AND p.is_featured=:featured"); params.addValue("featured", c.featured()); }
        if (c.newArrival() != null) { sql.append(" AND p.is_new_arrival=:newArrival"); params.addValue("newArrival", c.newArrival()); }
        if (c.bestSeller() != null) { sql.append(" AND p.is_best_seller=:bestSeller"); params.addValue("bestSeller", c.bestSeller()); }
    }

    private String productSelect() {
        return """
                SELECT p.id,p.sku,p.slug,p.name,p.price,p.discount_price,
                       p.is_featured,p.is_new_arrival,p.is_best_seller,
                       c.id category_id,c.name category_name,c.slug category_slug,
                       sc.id subcategory_id,sc.name subcategory_name,sc.slug subcategory_slug,
                       (SELECT pi.image_url FROM product_images pi WHERE pi.product_id=p.id
                        ORDER BY pi.is_primary DESC,pi.id LIMIT 1) primary_image_url,
                       EXISTS(SELECT 1 FROM product_sizes ps WHERE ps.product_id=p.id
                              AND ps.is_available=1 AND ps.stock_quantity>0) in_stock
                """;
    }

    private String orderBy(String sort) {
        return switch (sort) {
            case "price-asc" -> " ORDER BY " + EFFECTIVE_PRICE + ",p.id DESC";
            case "price-desc" -> " ORDER BY " + EFFECTIVE_PRICE + " DESC,p.id DESC";
            case "name-asc" -> " ORDER BY p.name,p.id";
            case "featured" -> " ORDER BY p.is_featured DESC,p.is_best_seller DESC,p.is_new_arrival DESC,p.id DESC";
            default -> " ORDER BY p.id DESC";
        };
    }

    private List<ProductSize> sizes(long productId) {
        return jdbc.sql("""
                SELECT size,(is_available=1 AND stock_quantity>0) in_stock
                FROM product_sizes WHERE product_id=:id ORDER BY id
                """).param("id", productId).query((rs, rowNum) -> new ProductSize(
                        rs.getString("size"), rs.getBoolean("in_stock"))).list();
    }

    private ProductIdentity productIdentity(String slug) {
        return jdbc.sql("""
                SELECT p.id,p.slug FROM products p
                JOIN categories c ON c.id=p.category_id AND c.is_active=1
                JOIN subcategories sc ON sc.id=p.subcategory_id AND sc.is_active=1
                WHERE p.slug=:slug AND p.is_active=1 AND p.publication_status='PUBLISHED'
                """).param("slug", slug).query((rs, rowNum) -> new ProductIdentity(
                        rs.getLong("id"), rs.getString("slug"))).optional()
                .orElseThrow(() -> ApiException.notFound("Product"));
    }

    private static CategorySummary category(ResultSet rs) throws SQLException {
        return new CategorySummary(rs.getLong("id"), rs.getString("name"), rs.getString("slug"),
                rs.getLong("subcategory_count"), rs.getLong("product_count"));
    }

    private static SubcategorySummary subcategory(ResultSet rs) throws SQLException {
        return new SubcategorySummary(rs.getLong("id"), rs.getString("name"), rs.getString("slug"),
                rs.getLong("category_id"), rs.getString("category_name"), rs.getString("category_slug"),
                rs.getLong("product_count"));
    }

    private static MoodSummary mood(ResultSet rs) throws SQLException {
        return new MoodSummary(rs.getLong("id"), rs.getString("name"), rs.getString("slug"),
                rs.getString("tagline"), rs.getString("personality_tagline"),
                rs.getString("banner_image_url"), rs.getString("color"), rs.getLong("product_count"));
    }

    private static ProductCard productCard(ResultSet rs) throws SQLException {
        long price = rs.getLong("price");
        Long discount = nullableLong(rs, "discount_price");
        return new ProductCard(rs.getLong("id"), rs.getString("sku"), rs.getString("slug"), rs.getString("name"),
                price, discount, CatalogPricing.effectivePrice(price, discount), CatalogPricing.discountPercent(price, discount),
                rs.getString("primary_image_url"), rs.getBoolean("in_stock"), rs.getBoolean("is_featured"),
                rs.getBoolean("is_new_arrival"), rs.getBoolean("is_best_seller"),
                new CategoryRef(rs.getLong("category_id"), rs.getString("category_name"), rs.getString("category_slug")),
                new SubcategoryRef(rs.getLong("subcategory_id"), rs.getString("subcategory_name"), rs.getString("subcategory_slug")));
    }

    private static ProductBase productBase(ResultSet rs) throws SQLException {
        long price = rs.getLong("price");
        Long discount = nullableLong(rs, "discount_price");
        GstRate gst = rs.getString("gst_name") == null ? null
                : new GstRate(rs.getString("gst_name"), rs.getString("hsn_code"), rs.getString("rate_percentage"));
        return new ProductBase(rs.getLong("id"), rs.getString("sku"), rs.getString("slug"), rs.getString("name"),
                rs.getString("description"), rs.getString("fabric_details"), rs.getString("color_name"),
                price, discount, rs.getBoolean("in_stock"), rs.getBoolean("is_featured"),
                rs.getBoolean("is_new_arrival"), rs.getBoolean("is_best_seller"), rs.getInt("return_window_days"),
                new CategoryRef(rs.getLong("category_id"), rs.getString("category_name"), rs.getString("category_slug")),
                new SubcategoryRef(rs.getLong("subcategory_id"), rs.getString("subcategory_name"), rs.getString("subcategory_slug")), gst);
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private record ProductIdentity(long id, String slug) {}

    private record ProductBase(long id, String sku, String slug, String name, String description,
                               String fabricDetails, String colorName, long price, Long discountPrice,
                               boolean inStock, boolean featured, boolean newArrival, boolean bestSeller,
                               int returnWindowDays, CategoryRef category, SubcategoryRef subcategory, GstRate gst) {
        ProductDetail detail(List<ProductImage> images, List<ProductSize> sizes, List<MoodRef> moods, SizeChart chart) {
            return new ProductDetail(id, sku, slug, name, description, fabricDetails, colorName, price, discountPrice,
                    CatalogPricing.effectivePrice(price, discountPrice), CatalogPricing.discountPercent(price, discountPrice),
                    inStock, featured, newArrival, bestSeller, returnWindowDays, category, subcategory,
                    gst, images, sizes, moods, chart);
        }
    }
}
