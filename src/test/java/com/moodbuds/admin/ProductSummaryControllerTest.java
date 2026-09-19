package com.moodbuds.admin;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.moodbuds.common.PageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductSummaryController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProductSummaryControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean ProductSummaryService service;

    @Test
    void returnsTheAdminProductListContract() throws Exception {
        var product = new ProductSummaryDtos.ProductSummary(
                7, "MB-TEST-7", "Test Jacket", 299900, 249900L, 12, 5,
                41L, "/api/v1/media/41/content", "Cool", "#7EB8F0", "PUBLISHED");
        when(service.list(0, 20, "jacket"))
                .thenReturn(PageResponse.of(List.of(product), 0, 20, 1));

        mockMvc.perform(get("/api/v1/admin/products/summary").param("q", "jacket"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sku").value("MB-TEST-7"))
                .andExpect(jsonPath("$.content[0].discountPrice").value(249900))
                .andExpect(jsonPath("$.content[0].stock").value(12))
                .andExpect(jsonPath("$.content[0].publicationStatus").value("PUBLISHED"));
    }
}
