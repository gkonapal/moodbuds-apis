package com.moodbuds.refund;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "moodbuds.refunds")
public record RefundProperties(int pollingBatchSize) {
    public RefundProperties {
        pollingBatchSize = pollingBatchSize <= 0 ? 100 : Math.min(pollingBatchSize, 500);
    }
}
