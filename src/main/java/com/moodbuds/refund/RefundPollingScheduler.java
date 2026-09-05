package com.moodbuds.refund;

import com.moodbuds.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RefundPollingScheduler {
    private static final Logger log = LoggerFactory.getLogger(RefundPollingScheduler.class);
    private final RefundStore store;
    private final RefundService refunds;
    private final RefundProperties properties;

    public RefundPollingScheduler(RefundStore store, RefundService refunds, RefundProperties properties) {
        this.store = store;
        this.refunds = refunds;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${moodbuds.refunds.polling-interval:PT5M}",
            initialDelayString = "${moodbuds.refunds.polling-initial-delay:PT1M}")
    public void reconcileRefunds() {
        for (long refundId : store.dueRefundIds(properties.pollingBatchSize())) {
            try {
                refunds.process(refundId);
            } catch (ApiException exception) {
                log.warn("Deferring Razorpay refund {}: {}", refundId, exception.code());
            } catch (RuntimeException exception) {
                log.error("Could not reconcile refund {}", refundId, exception);
            }
        }
    }
}
