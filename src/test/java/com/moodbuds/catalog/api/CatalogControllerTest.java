package com.moodbuds.catalog.api;

import static com.moodbuds.catalog.api.CatalogDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.moodbuds.catalog.CatalogService;
import com.moodbuds.catalog.ProductSearchCriteria;
import com.moodbuds.common.PageResponse;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CatalogController.class)
@AutoConfigureMockMvc(addFilters = false)
class CatalogControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean CatalogService catalog;

    @Test
    void returnsPagedProductCards() throws Exception {
        var card = new ProductCard(1, "MB-1", "blue-tee", "Blue Tee", 1_000, 800L,
                800, 20, "/blue.jpg", 4.5, 12, true, true, false, true,
                new CategoryRef(2, "Clothing", "clothing"), new SubcategoryRef(3, "Tees", "tees"));
        when(catalog.products(any())).thenReturn(PageResponse.of(List.of(card), 0, 20, 1));

        mockMvc.perform(get("/api/v1/products").param("mood", "cool").param("inStock", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].slug").value("blue-tee"))
                .andExpect(jsonPath("$.content[0].effectivePrice").value(800))
                .andExpect(jsonPath("$.content[0].inStock").value(true))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void rejectsUnsupportedSortBeforeQuerying() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("sort", "unsafe"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("INVALID_SORT"));
    }

    @Test
    void bindsCatalogFiltersIncludingMultipleSizesAndSale() throws Exception {
        when(catalog.products(any())).thenReturn(PageResponse.of(List.of(), 0, 20, 0));

        mockMvc.perform(get("/api/v1/products")
                        .param("category", "women")
                        .param("subcategory", "dresses")
                        .param("productSize", "M,L")
                        .param("minPrice", "99900")
                        .param("maxPrice", "199900")
                        .param("onSale", "true"))
                .andExpect(status().isOk());

        var criteria = ArgumentCaptor.forClass(ProductSearchCriteria.class);
        verify(catalog).products(criteria.capture());
        assertThat(criteria.getValue().category()).isEqualTo("women");
        assertThat(criteria.getValue().subcategory()).isEqualTo("dresses");
        assertThat(criteria.getValue().productSizes()).containsExactly("M", "L");
        assertThat(criteria.getValue().minPrice()).isEqualTo(99900L);
        assertThat(criteria.getValue().maxPrice()).isEqualTo(199900L);
        assertThat(criteria.getValue().onSale()).isTrue();
    }

    @Test
    void returnsProductDetailBySlug() throws Exception {
        var detail = new ProductDetail(1, "MB-1", "blue-tee", "Blue Tee", "Soft", "Cotton", "Blue", "#0000FF",
                1_000, null, 1_000, null, 4.5, 12, true, false, true, false, 7,
                new CategoryRef(2, "Clothing", "clothing"), new SubcategoryRef(3, "Tees", "tees"),
                null, List.of(new ProductImage(4, "/blue.jpg", true)),
                List.of(new ProductSize("M", true)), List.of(), null);
        when(catalog.product("blue-tee")).thenReturn(detail);

        mockMvc.perform(get("/api/v1/products/blue-tee"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("blue-tee"))
                .andExpect(jsonPath("$.sizes[0].size").value("M"));
    }
}
