package com.moodbuds.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class InventoryOverviewServiceTest {
    @Test
    void groupsSizesAndCalculatesStatusesAndSummary() {
        var products = List.of(
                new InventoryOverviewService.ProductRow(1, "MB-ONE", "One", "/one.jpg", List.of("Happy"), 1_000),
                new InventoryOverviewService.ProductRow(2, "MB-TWO", "Two", null, List.of(), 2_000),
                new InventoryOverviewService.ProductRow(3, "MB-THREE", "Three", null, List.of(), 3_000));
        var sizes = List.of(
                new InventoryOverviewService.SizeRow(10, 1, "S", 10, 5, true),
                new InventoryOverviewService.SizeRow(11, 1, "M", 3, 5, true),
                new InventoryOverviewService.SizeRow(12, 1, "L", 0, 5, false),
                new InventoryOverviewService.SizeRow(20, 2, "One size", 4, 5, false));

        var result = InventoryOverviewService.assemble(products, sizes);

        assertThat(result.summary().totalProducts()).isEqualTo(3);
        assertThat(result.summary().totalSizeVariants()).isEqualTo(4);
        assertThat(result.summary().totalUnits()).isEqualTo(17);
        assertThat(result.summary().inStockProducts()).isEqualTo(1);
        assertThat(result.summary().lowStockVariants()).isEqualTo(1);
        assertThat(result.summary().outOfStockVariants()).isEqualTo(1);
        assertThat(result.summary().stockValue()).isEqualTo(21_000);

        assertThat(result.products().get(0).status()).isEqualTo("LOW_STOCK");
        assertThat(result.products().get(0).sizes()).extracting(InventoryOverviewDtos.Size::status)
                .containsExactly("IN_STOCK", "LOW_STOCK", "OUT_OF_STOCK");
        assertThat(result.products().get(1).status()).isEqualTo("OUT_OF_STOCK");
        assertThat(result.products().get(1).sizes().getFirst().status()).isEqualTo("DISABLED");
        assertThat(result.products().get(2).status()).isEqualTo("OUT_OF_STOCK");
        assertThat(result.products().get(2).sizes()).isEmpty();
    }
}
