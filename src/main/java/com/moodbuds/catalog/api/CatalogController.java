package com.moodbuds.catalog.api;

import java.util.List;

import com.moodbuds.catalog.CatalogService;
import com.moodbuds.catalog.ProductSearchCriteria;
import com.moodbuds.catalog.api.CatalogDtos.*;
import com.moodbuds.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Public Catalog")
public class CatalogController {
    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/home")
    @Operation(summary = "Get public homepage catalog collections")
    HomeResponse home(@RequestParam(defaultValue = "12") int collectionSize) {
        return catalog.home(collectionSize);
    }

    @GetMapping("/categories")
    @Operation(summary = "List active categories")
    List<CategorySummary> categories() {
        return catalog.categories();
    }

    @GetMapping("/categories/{slug}")
    @Operation(summary = "Get an active category and its subcategories")
    CategoryDetail category(@PathVariable String slug) {
        return catalog.category(slug);
    }

    @GetMapping("/categories/{slug}/subcategories")
    @Operation(summary = "List active subcategories in a category")
    List<SubcategorySummary> subcategories(@PathVariable String slug) {
        return catalog.subcategories(slug);
    }

    @GetMapping("/subcategories/{slug}")
    @Operation(summary = "Get an active subcategory")
    SubcategorySummary subcategory(@PathVariable String slug) {
        return catalog.subcategory(slug);
    }

    @GetMapping("/moods")
    @Operation(summary = "List active moods")
    List<MoodSummary> moods() {
        return catalog.moods();
    }

    @GetMapping("/moods/{slug}")
    @Operation(summary = "Get an active mood")
    MoodSummary mood(@PathVariable String slug) {
        return catalog.mood(slug);
    }

    @GetMapping("/moods/{slug}/products")
    @Operation(summary = "List in-stock products for a mood")
    PageResponse<ProductCard> moodProducts(@PathVariable String slug,
                                           @RequestParam(required = false, name = "q") String query,
                                           @RequestParam(required = false) String category,
                                           @RequestParam(required = false) String subcategory,
                                           @RequestParam(required = false) String productSize,
                                           @RequestParam(required = false) String color,
                                           @RequestParam(required = false) Long minPrice,
                                           @RequestParam(required = false) Long maxPrice,
                                           @RequestParam(required = false) Boolean onSale,
                                           @RequestParam(defaultValue = "newest") String sort,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        catalog.mood(slug);
        return catalog.products(new ProductSearchCriteria(query, category, subcategory, slug, productSize, color,
                minPrice, maxPrice, true, null, null, null, onSale, sort, page, size));
    }

    @GetMapping({"/products", "/products/search"})
    @Operation(summary = "Search and filter active products")
    PageResponse<ProductCard> products(@RequestParam(required = false, name = "q") String query,
                                       @RequestParam(required = false) String category,
                                       @RequestParam(required = false) String subcategory,
                                       @RequestParam(required = false) String mood,
                                       @RequestParam(required = false) String productSize,
                                       @RequestParam(required = false) String color,
                                       @RequestParam(required = false) Long minPrice,
                                       @RequestParam(required = false) Long maxPrice,
                                       @RequestParam(required = false) Boolean inStock,
                                       @RequestParam(required = false) Boolean featured,
                                       @RequestParam(required = false) Boolean newArrival,
                                       @RequestParam(required = false) Boolean bestSeller,
                                       @RequestParam(required = false) Boolean onSale,
                                       @RequestParam(defaultValue = "newest") String sort,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return catalog.products(new ProductSearchCriteria(query, category, subcategory, mood, productSize, color,
                minPrice, maxPrice, inStock, featured, newArrival, bestSeller, onSale, sort, page, size));
    }

    @GetMapping("/products/{slug}")
    @Operation(summary = "Get public product detail by slug")
    ProductDetail product(@PathVariable String slug) {
        return catalog.product(slug);
    }

    @GetMapping("/products/{slug}/availability")
    @Operation(summary = "Get aggregate and per-size product availability")
    ProductAvailability availability(@PathVariable String slug) {
        return catalog.availability(slug);
    }

    @GetMapping("/products/{slug}/size-chart")
    @Operation(summary = "Get product-specific or inherited subcategory size chart")
    SizeChart sizeChart(@PathVariable String slug) {
        return catalog.sizeChart(slug);
    }
}
