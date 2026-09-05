package com.moodbuds.returns.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.moodbuds.returns.CustomerReturnService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CustomerReturnController.class)
@AutoConfigureMockMvc(addFilters = false)
class CustomerReturnControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean CustomerReturnService returns;

    @Test
    void createsReturnForAuthenticatedCustomer() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("customerId")).thenReturn(12L);
        var request = new ReturnDtos.CreateReturnRequest(ReturnDtos.ReturnReason.SIZE_DOES_NOT_FIT,
                "Need a different size", 4, List.of(), List.of(
                new ReturnDtos.ReturnItemRequest(8, 1, ReturnDtos.ReturnItemReason.WRONG_SIZE)));

        new CustomerReturnController(returns).create(jwt, "MB-1", "return-key-123", request);

        verify(returns).create(eq(12L), eq("MB-1"), eq("return-key-123"), eq(request));
    }

    @Test
    void returnsEligibilityForAuthenticatedCustomer() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("customerId")).thenReturn(12L);
        when(returns.eligibility(12L, "MB-1")).thenReturn(new ReturnDtos.ReturnEligibilityResponse(
                "MB-1", "DELIVERED", null, true, null, List.of()));

        var response = new CustomerReturnController(returns).eligibility(jwt, "MB-1");

        assertThat(response.canCreateReturn()).isTrue();
    }

    @Test
    void rejectsMissingIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/v1/customer/orders/MB-1/returns")
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("INVALID_REQUEST"));
    }

    @Test
    void rejectsEmptyReturnItems() throws Exception {
        mockMvc.perform(post("/api/v1/customer/orders/MB-1/returns")
                        .header("Idempotency-Key", "return-key-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"CHANGED_MIND\",\"pickupAddressId\":1,\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"));
    }

    private String validBody() {
        return """
                {"reason":"SIZE_DOES_NOT_FIT","pickupAddressId":1,
                 "items":[{"orderItemId":8,"quantity":1,"reason":"WRONG_SIZE"}]}
                """;
    }
}
