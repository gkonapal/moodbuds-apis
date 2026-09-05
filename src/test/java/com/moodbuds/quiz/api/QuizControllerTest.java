package com.moodbuds.quiz.api;

import static com.moodbuds.quiz.api.QuizDtos.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import com.moodbuds.quiz.QuizService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(QuizController.class)
@AutoConfigureMockMvc(addFilters = false)
class QuizControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean QuizService service;

    @Test
    void createsAnonymousQuizSession() throws Exception {
        var question = new QuestionResponse(1, 1, "What is your vibe?",
                List.of(new OptionResponse(1001, "A", "Powerful", 1)));
        when(service.createSession(any())).thenReturn(new CreateSessionResponse(
                "session-id", "a-valid-session-token-value", "IN_PROGRESS", 0, 10, 0,
                LocalDateTime.parse("2026-08-28T05:30:00"), question));

        mockMvc.perform(post("/api/v1/quiz/sessions"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").value("session-id"))
                .andExpect(jsonPath("$.totalQuestions").value(10))
                .andExpect(jsonPath("$.nextQuestion.order").value(1));
    }

    @Test
    void rejectsAnswerWithoutIds() throws Exception {
        mockMvc.perform(post("/api/v1/quiz/sessions/session-id/answers")
                        .header(QuizController.SESSION_TOKEN_HEADER, "a-valid-session-token-value")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"));
    }
}
