package com.moodbuds.admin;

import static com.moodbuds.admin.MoodAdminDtos.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.time.Instant;

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

@WebMvcTest(MoodAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
class MoodAdminControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean MoodAdminService service;

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

    private static MoodAdminView happy(boolean active) {
        return new MoodAdminView(1, "Happy", "happy", "😊", "Bright & cheerful",
                "You radiate positivity", "#FFD93D", 1, active,
                12, 8, 3, true, "/api/v1/moods/happy/banner");
    }

    @Test
    void listsActiveAndInactiveMoodsForAdmin() throws Exception {
        when(service.list()).thenReturn(List.of(happy(true), new MoodAdminView(2, "Calm", "calm", "🌊",
                null, null, "#70C0D0", 2, false, 0, 0, 0, false, null)));

        mockMvc.perform(get("/api/v1/admin/moods"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].emoji").value("😊"))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[1].active").value(false));
    }

    @Test
    void createsMoodWithDatabasePresentationFields() throws Exception {
        when(service.create(any(CreateMoodRequest.class), eq(7L))).thenReturn(happy(true));

        mockMvc.perform(post("/api/v1/admin/moods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Happy","emoji":"😊","tagline":"Bright & cheerful",
                                 "personalityTagline":"You radiate positivity","color":"#FFD93D",
                                 "displayOrder":1,"active":true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/admin/moods/1"))
                .andExpect(jsonPath("$.slug").value("happy"));
    }

    @Test
    void togglesMoodStatus() throws Exception {
        when(service.setStatus(1L, false, 7L)).thenReturn(happy(false));

        mockMvc.perform(patch("/api/v1/admin/moods/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }
}
