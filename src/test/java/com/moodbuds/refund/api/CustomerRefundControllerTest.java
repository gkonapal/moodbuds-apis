package com.moodbuds.refund.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moodbuds.refund.RefundService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class CustomerRefundControllerTest {
    @Test
    void scopesRefundLookupToAuthenticatedCustomer() {
        RefundService refunds = mock(RefundService.class);
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("customerId")).thenReturn(12L);

        new CustomerRefundController(refunds).get(jwt, 9L);

        verify(refunds).customerRefund(12L, 9L);
    }
}
