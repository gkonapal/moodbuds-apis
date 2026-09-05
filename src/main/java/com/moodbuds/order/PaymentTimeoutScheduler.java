package com.moodbuds.order;

import com.moodbuds.common.ApiException;
import com.moodbuds.payment.RazorpayPaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentTimeoutScheduler {
    private static final Logger log = LoggerFactory.getLogger(PaymentTimeoutScheduler.class);
    private final OrderReservationService reservations;
    private final RazorpayPaymentService payments;
    private final OrderLifecycleProperties properties;

    public PaymentTimeoutScheduler(OrderReservationService reservations, RazorpayPaymentService payments,
                                   OrderLifecycleProperties properties) {
        this.reservations = reservations;
        this.payments = payments;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${moodbuds.orders.payment-timeout-scan-interval:PT1M}",
            initialDelayString = "${moodbuds.orders.payment-timeout-scan-initial-delay:PT30S}")
    public void releaseExpiredReservations() {
        for (var candidate : reservations.expiredCandidates(properties.paymentTimeoutBatchSize())) {
            try {
                if (candidate.openProviderAttempt()) {
                    var status = payments.reconcileSystem(candidate.customerId(), candidate.orderNumber());
                    if ("CONFIRMED".equals(status.orderStatus())) continue;
                }
                reservations.expire(candidate.orderId());
            } catch (ApiException exception) {
                log.warn("Deferring payment timeout for order {}: {}", candidate.orderNumber(), exception.code());
            } catch (RuntimeException exception) {
                log.error("Could not process payment timeout for order {}", candidate.orderNumber(), exception);
            }
        }
    }
}
