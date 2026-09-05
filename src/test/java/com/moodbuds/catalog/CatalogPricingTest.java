package com.moodbuds.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CatalogPricingTest {
    @Test
    void calculatesOnlyRealDiscounts() {
        assertThat(CatalogPricing.effectivePrice(10_000, 7_500L)).isEqualTo(7_500);
        assertThat(CatalogPricing.discountPercent(10_000, 7_500L)).isEqualTo(25);
        assertThat(CatalogPricing.effectivePrice(10_000, 10_000L)).isEqualTo(10_000);
        assertThat(CatalogPricing.discountPercent(10_000, 10_000L)).isNull();
        assertThat(CatalogPricing.discountPercent(0, 0L)).isNull();
    }
}
