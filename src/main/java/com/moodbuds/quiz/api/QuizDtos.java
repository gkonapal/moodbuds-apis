package com.moodbuds.quiz.api;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public final class QuizDtos {
    private QuizDtos() {}

    public record MoodSummary(long id, String name, String slug, String tagline, String color) {}

    public record QuizConfigResponse(
            int totalQuestions,
            int branchQuestionCount,
            int universalQuestionCount,
            int sessionValidityHours,
            List<MoodSummary> possibleMoods) {}

    public record OptionResponse(long id, String key, String text, int sortOrder) {}

    public record QuestionResponse(
            long id,
            int order,
            String text,
            List<OptionResponse> options) {}

    public record CreateSessionResponse(
            String sessionId,
            String sessionToken,
            String status,
            int answeredQuestions,
            int totalQuestions,
            int progressPercent,
            LocalDateTime startedAt,
            QuestionResponse nextQuestion) {}

    public record SessionProgressResponse(
            String sessionId,
            String status,
            int answeredQuestions,
            int totalQuestions,
            int progressPercent,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            QuestionResponse nextQuestion) {}

    public record AnswerRequest(
            @NotNull @Positive Long questionId,
            @NotNull @Positive Long optionId) {}

    public record MoodScore(long moodId, String name, String slug, int score) {}

    public record MoodResult(
            long id,
            String name,
            String slug,
            String tagline,
            String personalityTagline,
            String bannerImageUrl,
            String color) {}

    public record RecommendedProduct(
            long id,
            String sku,
            String slug,
            String name,
            long price,
            Long discountPrice,
            String primaryImageUrl,
            long availableStock) {}

    public record QuizResultResponse(
            String sessionId,
            String status,
            LocalDateTime completedAt,
            MoodResult mood,
            List<MoodScore> scoreBreakdown,
            List<RecommendedProduct> recommendedProducts) {}
}
