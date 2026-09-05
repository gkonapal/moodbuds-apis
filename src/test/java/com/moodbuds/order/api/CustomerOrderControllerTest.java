package com.moodbuds.order.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moodbuds.order.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CustomerOrderController.class)
@AutoConfigureMockMvc(addFilters = false)
class CustomerOrderControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean OrderService orders;

    @Test
    void placesOrderForAuthenticatedCustomer() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("customerId")).thenReturn(12L);
        var request = new OrderDtos.PlaceOrderRequest(4, OrderDtos.PaymentMethod.UPI);

        new CustomerOrderController(orders).place(jwt, "checkout-key-123", request);

        verify(orders).place(eq(12L), eq("checkout-key-123"), eq(request));
    }

    @Test
    void rejectsMissingIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/v1/customer/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":1,\"paymentMethod\":\"UPI\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("INVALID_REQUEST"));
    }

    @Test
    void rejectsInvalidOrderRequest() throws Exception {
        mockMvc.perform(post("/api/v1/customer/orders").header("Idempotency-Key", "checkout-key-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":0,\"paymentMethod\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"));
    }
}
