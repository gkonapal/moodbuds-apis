package com.moodbuds.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class OrderLifecyclePropertiesTest {
    @Test
    void appliesSafeDefaultsAndBatchBound() {
        var defaults = new OrderLifecycleProperties(null, 0);
        var bounded = new OrderLifecycleProperties(Duration.ofMinutes(5), 1000);

        assertThat(defaults.paymentTimeout()).isEqualTo(Duration.ofMinutes(15));
        assertThat(defaults.paymentTimeoutBatchSize()).isEqualTo(100);
        assertThat(bounded.paymentTimeoutBatchSize()).isEqualTo(500);
    }

    @Test
    void rejectsNonPositiveTimeout() {
        assertThatThrownBy(() -> new OrderLifecycleProperties(Duration.ZERO, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
