package com.moodbuds.order;

import static com.moodbuds.order.api.OrderDtos.*;

import com.moodbuds.payment.RazorpayPaymentService;
import org.springframework.stereotype.Service;

@Service
public class OrderCancellationService {
    private final OrderReservationService reservations;
    private final RazorpayPaymentService payments;
    private final OrderService orders;

    public OrderCancellationService(OrderReservationService reservations, RazorpayPaymentService payments,
                                    OrderService orders) {
        this.reservations = reservations;
        this.payments = payments;
        this.orders = orders;
    }

    public CancellationEligibilityResponse eligibility(long customerId, String orderNumber) {
        return reservations.eligibility(customerId, orderNumber);
    }

    public OrderDetailResponse cancel(long customerId, String orderNumber, CancelOrderRequest request) {
        var candidate = reservations.candidate(customerId, orderNumber);
        if (candidate.openProviderAttempt()) {
            payments.reconcileSystem(candidate.customerId(), candidate.orderNumber());
        }
        reservations.cancelByCustomer(customerId, orderNumber, request.reason());
        return orders.get(customerId, orderNumber);
    }

    public void cancelByAdmin(String orderNumber, long adminId, String reason) {
        var candidate = reservations.candidate(orderNumber);
        if (candidate.openProviderAttempt()) {
            payments.reconcileSystem(candidate.customerId(), candidate.orderNumber());
        }
        reservations.cancelByAdmin(orderNumber, adminId, reason);
    }
}
