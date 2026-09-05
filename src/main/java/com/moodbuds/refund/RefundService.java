package com.moodbuds.refund;

import static com.moodbuds.refund.api.RefundDtos.*;

import com.moodbuds.common.ApiException;
import com.moodbuds.common.PageResponse;
import com.moodbuds.customer.CustomerProfileService;
import com.moodbuds.payment.RazorpayGateway;
import org.springframework.stereotype.Service;

@Service
public class RefundService {
    private final RefundStore store;
    private final RazorpayGateway gateway;
    private final CustomerProfileService customers;

    public RefundService(RefundStore store, RazorpayGateway gateway, CustomerProfileService customers) {
        this.store = store;
        this.gateway = gateway;
        this.customers = customers;
    }

    public RefundResponse initiate(long returnId, String idempotencyKey, InitiateRefundRequest request) {
        long refundId = store.prepare(returnId, idempotencyKey.trim(), request.speed());
        return process(refundId);
    }

    public RefundResponse reconcile(long refundId) {
        return process(refundId);
    }

    public RefundResponse process(long refundId) {
        var operation = store.recordAttempt(refundId);
        if ("PROCESSED".equals(operation.status()) || "FAILED".equals(operation.status())) {
            return store.adminRefund(refundId);
        }
        try {
            var provider = operation.gatewayRefundId() == null
                    ? gateway.createRefund(operation.gatewayPaymentId(), operation.amount(),
                            "MB-RF-" + operation.id(), operation.reason(), operation.idempotencyKey(),
                            operation.speed().name())
                    : gateway.fetchRefund(operation.gatewayRefundId());
            return store.apply(refundId, provider);
        } catch (ApiException exception) {
            store.recordProviderFailure(refundId, exception);
            throw exception;
        }
    }

    public RefundResponse customerRefund(long customerId, long refundId) {
        customers.requireActive(customerId);
        return store.customerRefund(customerId, refundId);
    }

    public PageResponse<RefundResponse> customerRefunds(long customerId, int page, int size) {
        customers.requireActive(customerId);
        return store.customerRefunds(customerId, page, size);
    }

    public RefundResponse adminRefund(long refundId) { return store.adminRefund(refundId); }
    public PageResponse<RefundResponse> adminRefunds(int page, int size) { return store.adminRefunds(page, size); }
}
