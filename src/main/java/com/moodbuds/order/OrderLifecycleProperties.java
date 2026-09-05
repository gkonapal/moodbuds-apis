package com.moodbuds.order;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "moodbuds.orders")
public record OrderLifecycleProperties(Duration paymentTimeout, int paymentTimeoutBatchSize) {
    public OrderLifecycleProperties {
        paymentTimeout = paymentTimeout == null ? Duration.ofMinutes(15) : paymentTimeout;
        if (paymentTimeout.isZero() || paymentTimeout.isNegative()) {
            throw new IllegalArgumentException("Payment timeout must be positive");
        }
        paymentTimeoutBatchSize = paymentTimeoutBatchSize <= 0 ? 100 : Math.min(paymentTimeoutBatchSize, 500);
    }
}
