package com.moodbuds.auth;

import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminAuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminAuthControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean AdminAuthService service;

    @Test
    void rejectsBlankCredentials() throws Exception {
        mockMvc.perform(post("/api/v1/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"));
    }

    @Test
    void returnsTokenForValidCredentials() throws Exception {
        var response = new LoginResponse("token", "Bearer", Instant.parse("2030-01-01T00:00:00Z"),
                new LoginResponse.AdminSummary(1, "owner", "Owner", "SUPER_ADMIN", Set.of("admins.manage")));
        when(service.login(any())).thenReturn(response);
        mockMvc.perform(post("/api/v1/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"owner\",\"password\":\"long-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("token"))
                .andExpect(jsonPath("$.admin.role").value("SUPER_ADMIN"));
    }
}
