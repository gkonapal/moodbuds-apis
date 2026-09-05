package com.moodbuds.quiz.api;

import static com.moodbuds.quiz.api.QuizDtos.*;

import com.moodbuds.quiz.QuizService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/quiz")
@Validated
public class QuizController {
    public static final String SESSION_TOKEN_HEADER = "X-Quiz-Session-Token";
    private final QuizService service;

    public QuizController(QuizService service) {
        this.service = service;
    }

    @GetMapping("/config")
    QuizConfigResponse config() {
        return service.config();
    }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    CreateSessionResponse createSession(HttpServletRequest request) {
        return service.createSession(request);
    }

    @GetMapping("/sessions/{sessionId}")
    SessionProgressResponse progress(@PathVariable String sessionId,
                                     @RequestHeader(SESSION_TOKEN_HEADER) @Size(min=20,max=200) String sessionToken) {
        return service.getProgress(sessionId, sessionToken);
    }

    @GetMapping("/sessions/{sessionId}/question")
    QuestionResponse question(@PathVariable String sessionId,
                              @RequestHeader(SESSION_TOKEN_HEADER) @Size(min=20,max=200) String sessionToken) {
        return service.getNextQuestion(sessionId, sessionToken);
    }

    @PostMapping("/sessions/{sessionId}/answers")
    SessionProgressResponse answer(@PathVariable String sessionId,
                                   @RequestHeader(SESSION_TOKEN_HEADER) @Size(min=20,max=200) String sessionToken,
                                   @Valid @RequestBody AnswerRequest request) {
        return service.answer(sessionId, sessionToken, request);
    }

    @PostMapping("/sessions/{sessionId}/complete")
    QuizResultResponse complete(@PathVariable String sessionId,
                                @RequestHeader(SESSION_TOKEN_HEADER) @Size(min=20,max=200) String sessionToken) {
        return service.complete(sessionId, sessionToken);
    }

    @GetMapping("/sessions/{sessionId}/result")
    QuizResultResponse result(@PathVariable String sessionId,
                              @RequestHeader(SESSION_TOKEN_HEADER) @Size(min=20,max=200) String sessionToken) {
        return service.getResult(sessionId, sessionToken);
    }
}
