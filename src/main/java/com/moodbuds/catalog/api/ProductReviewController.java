package com.moodbuds.catalog.api;

import com.moodbuds.catalog.CatalogService;
import com.moodbuds.catalog.ProductReviewService;
import com.moodbuds.catalog.ProductReviewService.ReviewResponse;
import com.moodbuds.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products/{slug}/reviews")
@Tag(name = "Product Reviews")
public class ProductReviewController {
    private final CatalogService catalog;
    private final ProductReviewService reviews;

    public ProductReviewController(CatalogService catalog, ProductReviewService reviews) {
        this.catalog = catalog;
        this.reviews = reviews;
    }

    @GetMapping
    @Operation(summary = "Get product reviews with pagination")
    PageResponse<ReviewResponse> productReviews(@PathVariable String slug,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "10") int size) {
        var product = catalog.product(slug);
        if (size > 100) size = 100;
        if (size < 1) size = 10;
        if (page < 0) page = 0;
        return reviews.productReviews(product.id(), page, size);
    }
}
