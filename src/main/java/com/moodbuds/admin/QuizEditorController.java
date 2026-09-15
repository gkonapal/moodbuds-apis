package com.moodbuds.admin;

import static com.moodbuds.admin.QuizAdminDtos.*;

import com.moodbuds.auth.CurrentAdmin;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/quiz/editor")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class QuizEditorController {
    private final QuizEditorService service;

    public QuizEditorController(QuizEditorService service) {
        this.service = service;
    }

    @GetMapping
    QuizEditorResponse editor() {
        return service.editor();
    }

    @PutMapping("/questions/{questionId}")
    QuizEditorResponse update(@PathVariable long questionId,
                              @Valid @RequestBody UpdateQuestionRequest request,
                              @AuthenticationPrincipal Jwt jwt) {
        return service.updateQuestion(questionId, request, CurrentAdmin.id(jwt));
    }
}
