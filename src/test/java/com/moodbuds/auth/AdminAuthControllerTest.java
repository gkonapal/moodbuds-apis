package com.moodbuds.auth;

import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminAuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminAuthControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean AdminAuthService service;

    @BeforeEach
    void authenticateAdmin() {
        var jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .claim("adminId", 7L)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

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

    @Test
    void returnsCurrentAdminForValidSession() throws Exception {
        var admin = new LoginResponse.AdminSummary(7, "manager", "Store Manager", "MANAGER", Set.of("catalog.read"));
        when(service.current(7)).thenReturn(admin);
        mockMvc.perform(get("/api/v1/admin/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("manager"))
                .andExpect(jsonPath("$.role").value("MANAGER"));
    }
}
