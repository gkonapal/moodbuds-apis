package com.moodbuds.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moodbuds.common.ApiException;
import org.junit.jupiter.api.Test;

class ProductSearchCriteriaTest {
    @Test
    void normalizesSearchValues() {
        var criteria = new ProductSearchCriteria("  tee ", " women ", null, null, null, null,
                100L, 500L, true, null, null, null, " PRICE-ASC ", 0, 20);
        assertThat(criteria.query()).isEqualTo("tee");
        assertThat(criteria.category()).isEqualTo("women");
        assertThat(criteria.sort()).isEqualTo("price-asc");
    }

    @Test
    void rejectsInvalidRangesSortAndPagination() {
        assertThatThrownBy(() -> criteria(500L, 100L, "newest", 0, 20)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> criteria(null, null, "DROP TABLE products", 0, 20)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> criteria(null, null, "newest", -1, 20)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> criteria(null, null, "newest", 0, 101)).isInstanceOf(ApiException.class);
    }

    private ProductSearchCriteria criteria(Long min, Long max, String sort, int page, int size) {
        return new ProductSearchCriteria(null, null, null, null, null, null, min, max,
                null, null, null, null, sort, page, size);
    }
}
