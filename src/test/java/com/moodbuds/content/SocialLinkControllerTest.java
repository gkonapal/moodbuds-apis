package com.moodbuds.content;

import static com.moodbuds.content.SocialLinkDtos.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SocialLinkController.class)
@AutoConfigureMockMvc(addFilters = false)
class SocialLinkControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean SocialLinkService service;

    @Test
    void returnsEnabledPublicLinks() throws Exception {
        when(service.publicLinks()).thenReturn(List.of(new SocialLink(1, "instagram", "Instagram",
                "https://www.instagram.com/myntra/", "instagram", 10, true, Instant.parse("2026-09-19T00:00:00Z"))));

        mockMvc.perform(get("/api/v1/content/social-links"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].platform").value("instagram"))
                .andExpect(jsonPath("$[0].url").value("https://www.instagram.com/myntra/"));
    }
}
