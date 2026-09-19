package com.moodbuds.admin;

import static com.moodbuds.content.SocialLinkDtos.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import com.moodbuds.content.SocialLinkService;
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

@WebMvcTest(SocialLinkAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
class SocialLinkAdminControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean SocialLinkService service;

    @BeforeEach
    void authenticate() {
        var jwt = Jwt.withTokenValue("test").header("alg", "none").claim("adminId", 5L)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void returnsAllConfiguredLinks() throws Exception {
        when(service.adminLinks()).thenReturn(links());
        mockMvc.perform(get("/api/v1/admin/content/social-links"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].enabled").value(true));
    }

    @Test
    void updatesLinksForCurrentAdmin() throws Exception {
        when(service.update(any(UpdateSocialLinksRequest.class), eq(5L))).thenReturn(links());
        mockMvc.perform(put("/api/v1/admin/content/social-links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"links":[{"id":1,"url":"https://www.instagram.com/myntra/","enabled":true}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].displayName").value("Instagram"));
    }

    private static List<SocialLink> links() {
        return List.of(new SocialLink(1, "instagram", "Instagram", "https://www.instagram.com/myntra/",
                "instagram", 10, true, Instant.parse("2026-09-19T00:00:00Z")));
    }
}
