package com.moodbuds.admin;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class QuizAdminDtos {
    private QuizAdminDtos() {}

    public record MoodChoice(long id, String name, String slug, String color) {}
    public record PathChoice(String key, String name, String description) {}
    public record MoodWeight(long moodId, String moodName, String moodSlug, String color, int score) {}
    public record OptionEditor(long id, String key, String text, int sortOrder, String routesToPath,
                               List<MoodWeight> weights) {}
    public record QuestionEditor(long id, int order, String pathKey, String text,
                                 List<OptionEditor> options) {}
    public record StructureStatus(boolean valid, List<String> issues) {}
    public record QuizEditorResponse(int questionsPerQuiz, List<PathChoice> paths, List<MoodChoice> moods,
                                     List<QuestionEditor> questions, StructureStatus structure) {}

    public record WeightUpdate(@Positive long moodId, @Min(1) @Max(5) int score) {}
    public record OptionUpdate(@Positive long id, @NotBlank @Size(max = 500) String text,
                               @NotNull @Size(min = 1) List<@Valid WeightUpdate> weights) {}
    public record UpdateQuestionRequest(@NotBlank @Size(max = 500) String questionText,
                                        @NotNull @Size(min = 4, max = 4)
                                        List<@Valid OptionUpdate> options) {}
}
