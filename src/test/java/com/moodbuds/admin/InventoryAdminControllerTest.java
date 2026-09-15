package com.moodbuds.admin;

import static com.moodbuds.admin.InventoryOverviewDtos.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.moodbuds.audit.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InventoryAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
class InventoryAdminControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean JdbcClient jdbc;
    @MockBean AuditService audit;
    @MockBean InventoryOverviewService overviewService;

    @Test
    void returnsProductsWithExpandableSizeInventory() throws Exception {
        var size = new Size(10, "M", 3, 5, true, "LOW_STOCK");
        var product = new Product(1, "MB-ONE", "One", "/one.jpg", List.of("Happy"),
                3, 3_000, "LOW_STOCK", List.of(size));
        when(overviewService.overview()).thenReturn(new Overview(
                new Summary(1, 1, 3, 1, 1, 0, 3_000), List.of(product)));

        mockMvc.perform(get("/api/v1/admin/inventory/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalSizeVariants").value(1))
                .andExpect(jsonPath("$.products[0].totalStock").value(3))
                .andExpect(jsonPath("$.products[0].sizes[0].size").value("M"))
                .andExpect(jsonPath("$.products[0].sizes[0].status").value("LOW_STOCK"));
    }
}
