package com.moodbuds.content;

import static com.moodbuds.content.SiteContentDtos.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SiteContentController.class)
@AutoConfigureMockMvc(addFilters = false)
class SiteContentControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean SiteContentService service;

    @Test
    void returnsPublicAboutContent() throws Exception {
        when(service.about()).thenReturn(new AboutContent("About MoodBuds", "Feel it. Wear it.",
                "Our story", "Our mission", Instant.parse("2026-09-15T10:00:00Z")));

        mockMvc.perform(get("/api/v1/content/about-us"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headline").value("Feel it. Wear it."))
                .andExpect(jsonPath("$.story").value("Our story"));
    }

    @Test
    void returnsPublicContactContent() throws Exception {
        when(service.contact()).thenReturn(new ContactContent("Contact Us", "We are here.",
                "hello@moodbuds.com", "1800-MOOD-BUD", "Mon-Sat", "Bengaluru",
                Instant.parse("2026-09-15T10:00:00Z")));

        mockMvc.perform(get("/api/v1/content/contact-us"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supportEmail").value("hello@moodbuds.com"))
                .andExpect(jsonPath("$.registeredAddress").value("Bengaluru"));
    }
}
