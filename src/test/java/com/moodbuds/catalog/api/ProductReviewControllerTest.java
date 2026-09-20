package com.moodbuds.catalog.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import com.moodbuds.catalog.ProductReviewService;
import com.moodbuds.catalog.ProductReviewService.ReviewResponse;
import com.moodbuds.common.PageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductReviewController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProductReviewControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean ProductReviewService reviews;

    @Test
    void returnsPagedReviewsWithoutCustomerIdentity() throws Exception {
        var review = new ReviewResponse(7, "G", 5, "Excellent", "Perfect fit",
                true, 2, Instant.parse("2026-09-19T10:00:00Z"));
        when(reviews.productReviews("blue-tee", 0, 3))
                .thenReturn(PageResponse.of(List.of(review), 0, 3, 1));

        mockMvc.perform(get("/api/v1/products/blue-tee/reviews").param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].userInitial").value("G"))
                .andExpect(jsonPath("$.content[0].userId").doesNotExist())
                .andExpect(jsonPath("$.content[0].rating").value(5))
                .andExpect(jsonPath("$.content[0].verifiedPurchase").value(true))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void normalizesUnsafePagination() throws Exception {
        when(reviews.productReviews("blue-tee", 0, 100))
                .thenReturn(PageResponse.of(List.of(), 0, 100, 0));

        mockMvc.perform(get("/api/v1/products/blue-tee/reviews")
                        .param("page", "-2").param("size", "500"))
                .andExpect(status().isOk());

        verify(reviews).productReviews("blue-tee", 0, 100);
    }
}
