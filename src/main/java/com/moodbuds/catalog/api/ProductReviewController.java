package com.moodbuds.catalog.api;

import com.moodbuds.catalog.ProductReviewService;
import com.moodbuds.catalog.ProductReviewService.ReviewResponse;
import com.moodbuds.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products/{slug}/reviews")
@Tag(name = "Product Reviews")
public class ProductReviewController {
    private final ProductReviewService reviews;

    public ProductReviewController(ProductReviewService reviews) {
        this.reviews = reviews;
    }

    @GetMapping
    @Operation(summary = "Get published product reviews with pagination")
    PageResponse<ReviewResponse> productReviews(@PathVariable String slug,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "10") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size < 1 ? 10 : Math.min(size, 100);
        return reviews.productReviews(slug, safePage, safeSize);
    }
}
