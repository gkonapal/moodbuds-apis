package com.moodbuds.admin;

import static com.moodbuds.content.SiteContentDtos.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import com.moodbuds.content.SiteContentService;
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

@WebMvcTest(SiteContentAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
class SiteContentAdminControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean SiteContentService service;

    @BeforeEach
    void authenticate() {
        var jwt = Jwt.withTokenValue("test").header("alg", "none").claim("adminId", 5L)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void returnsCombinedAdminContent() throws Exception {
        when(service.admin()).thenReturn(content());
        mockMvc.perform(get("/api/v1/admin/content/about-contact"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.about.headline").value("Feel it. Wear it."))
                .andExpect(jsonPath("$.contact.supportPhone").value("1800-MOOD-BUD"));
    }

    @Test
    void updatesBothPagesTogether() throws Exception {
        when(service.update(any(UpdateContentRequest.class), eq(5L))).thenReturn(content());
        mockMvc.perform(put("/api/v1/admin/content/about-contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"about":{"title":"About MoodBuds","headline":"Feel it. Wear it.",
                                  "story":"Our story","mission":"Our mission"},
                                 "contact":{"title":"Contact Us","intro":"We are here.",
                                  "supportEmail":"hello@moodbuds.com","supportPhone":"1800-MOOD-BUD",
                                  "supportHours":"Mon-Sat","registeredAddress":"Bengaluru"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedBy").value("superadmin"));
    }

    private static AdminContent content() {
        var updatedAt = Instant.parse("2026-09-15T10:00:00Z");
        return new AdminContent(
                new AboutContent("About MoodBuds", "Feel it. Wear it.", "Our story", "Our mission", updatedAt),
                new ContactContent("Contact Us", "We are here.", "hello@moodbuds.com", "1800-MOOD-BUD",
                        "Mon-Sat", "Bengaluru", updatedAt),
                "superadmin", updatedAt);
    }
}
