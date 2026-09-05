package com.moodbuds.order;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyLong;

import java.time.Duration;
import java.util.List;

import com.moodbuds.payment.RazorpayPaymentService;
import com.moodbuds.payment.api.PaymentDtos.PaymentStatusResponse;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PaymentTimeoutSchedulerTest {
    private final OrderReservationService reservations = Mockito.mock(OrderReservationService.class);
    private final RazorpayPaymentService payments = Mockito.mock(RazorpayPaymentService.class);
    private final PaymentTimeoutScheduler scheduler = new PaymentTimeoutScheduler(reservations, payments,
            new OrderLifecycleProperties(Duration.ofMinutes(15), 100));

    @Test
    void releasesExpiredReservationWithoutProviderAttempt() {
        var candidate = new OrderReservationService.CancellationCandidate(7, 9, "MB-7",
                "PENDING_PAYMENT", false);
        when(reservations.expiredCandidates(100)).thenReturn(List.of(candidate));

        scheduler.releaseExpiredReservations();

        verify(reservations).expire(7);
        verify(payments, never()).reconcileSystem(anyLong(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void doesNotReleaseWhenReconciliationFindsCapturedPayment() {
        var candidate = new OrderReservationService.CancellationCandidate(7, 9, "MB-7",
                "PENDING_PAYMENT", true);
        when(reservations.expiredCandidates(100)).thenReturn(List.of(candidate));
        when(payments.reconcileSystem(9, "MB-7")).thenReturn(new PaymentStatusResponse(
                "MB-7", "CONFIRMED", "RAZORPAY", "order_1", "pay_1", "UPI", "SUCCESS",
                1000, "INR", false, null, null, null));

        scheduler.releaseExpiredReservations();

        verify(reservations, never()).expire(7);
    }
}
