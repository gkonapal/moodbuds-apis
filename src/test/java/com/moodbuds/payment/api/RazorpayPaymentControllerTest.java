package com.moodbuds.payment.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moodbuds.payment.RazorpayPaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RazorpayPaymentController.class)
@AutoConfigureMockMvc(addFilters = false)
class RazorpayPaymentControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean RazorpayPaymentService payments;

    @Test
    void initiatesForAuthenticatedCustomer() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("customerId")).thenReturn(21L);

        new RazorpayPaymentController(payments).initiate(jwt, "MB-1", "payment-key-123");

        verify(payments).initiate(21L, "MB-1", "payment-key-123");
    }

    @Test
    void rejectsMissingPaymentIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/v1/customer/orders/MB-1/payments/razorpay"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("INVALID_REQUEST"));
    }

    @Test
    void rejectsIncompleteVerificationPayload() throws Exception {
        mockMvc.perform(post("/api/v1/customer/orders/MB-1/payments/razorpay/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"razorpayOrderId\":\"order_1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"));
    }
}
