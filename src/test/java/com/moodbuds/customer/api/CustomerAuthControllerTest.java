package com.moodbuds.customer.api;

import static com.moodbuds.customer.api.CustomerDtos.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import com.moodbuds.customer.CustomerAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CustomerAuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class CustomerAuthControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean CustomerAuthService auth;

    @Test
    void registersValidCustomer() throws Exception {
        var profile = new CustomerProfile(7, "buyer@example.com", false, null, false,
                "Mood", "Buyer", null, Gender.UNSPECIFIED, true,
                Instant.parse("2026-08-29T10:00:00Z"), Instant.parse("2026-08-29T10:00:00Z"));
        when(auth.register(any())).thenReturn(new AuthResponse("access", "refresh", "Bearer",
                Instant.parse("2026-08-29T10:15:00Z"), Instant.parse("2026-09-28T10:00:00Z"), profile));

        mockMvc.perform(post("/api/v1/customer/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"buyer@example.com","password":"strong-password","firstName":"Mood","lastName":"Buyer"}
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("access"))
                .andExpect(jsonPath("$.refreshToken").value("refresh"))
                .andExpect(jsonPath("$.customer.email").value("buyer@example.com"));
    }

    @Test
    void rejectsWeakRegistrationData() throws Exception {
        mockMvc.perform(post("/api/v1/customer/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"not-email","password":"short","firstName":"","lastName":""}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"));
    }

    @Test
    void rejectsBlankRefreshToken() throws Exception {
        mockMvc.perform(post("/api/v1/customer/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"));
    }
}
