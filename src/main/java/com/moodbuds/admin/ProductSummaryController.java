package com.moodbuds.admin;

import com.moodbuds.common.PageResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/products")
public class ProductSummaryController {
    private final ProductSummaryService service;

    public ProductSummaryController(ProductSummaryService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('catalog.read') or hasRole('SUPER_ADMIN')")
    PageResponse<ProductSummaryDtos.ProductSummary> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false, name = "q") String query) {
        return service.list(page, size, query);
    }
}
