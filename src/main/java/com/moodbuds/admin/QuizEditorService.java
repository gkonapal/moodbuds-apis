package com.moodbuds.admin;

import static com.moodbuds.admin.QuizAdminDtos.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.moodbuds.audit.AuditService;
import com.moodbuds.common.ApiException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuizEditorService {
    private static final int QUESTIONS_PER_QUIZ = 5;
    private static final List<String> PATH_KEYS = List.of("A", "B", "C", "D");
    private static final Set<String> OPTION_KEYS = Set.of("A", "B", "C", "D");

    private final JdbcClient jdbc;
    private final AuditService audit;

    public QuizEditorService(JdbcClient jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    public QuizEditorResponse editor() {
        var paths = jdbc.sql("SELECT path_key, name, description FROM quiz_paths ORDER BY path_key")
                .query((rs, row) -> new PathChoice(rs.getString("path_key"), rs.getString("name"),
                        rs.getString("description"))).list();
        var moods = jdbc.sql("SELECT id, name, slug, color FROM moods WHERE is_active=1 ORDER BY id")
                .query((rs, row) -> new MoodChoice(rs.getLong("id"), rs.getString("name"),
                        rs.getString("slug"), rs.getString("color"))).list();
        var questions = jdbc.sql("""
                SELECT id, question_order, path_key, question_text
                FROM quiz_questions
                WHERE is_active=1 AND (
                    (question_order=1 AND path_key IS NULL)
                    OR (question_order BETWEEN 2 AND 5 AND path_key IN ('A','B','C','D'))
                )
                ORDER BY question_order, path_key, id
                """).query((rs, row) -> new QuestionEditor(
                        rs.getLong("id"), rs.getInt("question_order"), rs.getString("path_key"),
                        rs.getString("question_text"), options(rs.getLong("id")))).list();
        return new QuizEditorResponse(QUESTIONS_PER_QUIZ, paths, moods, questions, validate(questions, paths));
    }

    @Transactional
    public QuizEditorResponse updateQuestion(long questionId, UpdateQuestionRequest request, long actorId) {
        var question = jdbc.sql("""
                SELECT id, question_order, path_key, question_text
                FROM quiz_questions
                WHERE id=:id AND is_active=1 AND (
                    (question_order=1 AND path_key IS NULL)
                    OR (question_order BETWEEN 2 AND 5 AND path_key IN ('A','B','C','D'))
                )
                FOR UPDATE
                """).param("id", questionId).query().listOfRows().stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Editable quiz question"));

        var currentOptions = jdbc.sql("""
                SELECT id FROM quiz_options
                WHERE question_id=:questionId AND is_active=1
                ORDER BY sort_order, id
                FOR UPDATE
                """).param("questionId", questionId).query(Long.class).list();
        var requestedOptionIds = request.options().stream().map(OptionUpdate::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (request.options().size() != requestedOptionIds.size()
                || !requestedOptionIds.equals(new LinkedHashSet<>(currentOptions))) {
            throw ApiException.badRequest("QUIZ_OPTIONS_FIXED",
                    "All four existing options must be submitted exactly once");
        }

        for (var option : request.options()) {
            var moodIds = option.weights().stream().map(WeightUpdate::moodId)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            if (moodIds.size() != option.weights().size()) {
                throw ApiException.badRequest("DUPLICATE_MOOD_WEIGHT",
                        "A mood can be assigned only once to an option");
            }
            long activeMoodCount = jdbc.sql("SELECT COUNT(*) FROM moods WHERE is_active=1 AND id IN (:ids)")
                    .param("ids", moodIds).query(Long.class).single();
            if (activeMoodCount != moodIds.size()) {
                throw ApiException.badRequest("INVALID_MOOD_WEIGHT", "Every weight must reference an active mood");
            }
        }

        jdbc.sql("UPDATE quiz_questions SET question_text=:text WHERE id=:id")
                .param("text", request.questionText().trim()).param("id", questionId).update();
        for (var option : request.options()) {
            jdbc.sql("UPDATE quiz_options SET option_text=:text WHERE id=:id AND question_id=:questionId")
                    .param("text", option.text().trim()).param("id", option.id())
                    .param("questionId", questionId).update();
            jdbc.sql("DELETE FROM quiz_option_mood_weights WHERE option_id=:optionId")
                    .param("optionId", option.id()).update();
            for (var weight : option.weights()) {
                jdbc.sql("""
                        INSERT INTO quiz_option_mood_weights(option_id, mood_id, score)
                        VALUES (:optionId, :moodId, :score)
                        """).param("optionId", option.id()).param("moodId", weight.moodId())
                        .param("score", weight.score()).update();
            }
        }
        audit.record(actorId, "quiz.question_bundle_updated", "quiz_question", questionId,
                question, Map.of("questionText", request.questionText(), "options", request.options()));
        return editor();
    }

    private List<OptionEditor> options(long questionId) {
        return jdbc.sql("""
                SELECT id, option_key, option_text, sort_order, routes_to_path
                FROM quiz_options
                WHERE question_id=:questionId AND is_active=1
                ORDER BY sort_order, id
                """).param("questionId", questionId).query((rs, row) -> new OptionEditor(
                        rs.getLong("id"), rs.getString("option_key"), rs.getString("option_text"),
                        rs.getInt("sort_order"), rs.getString("routes_to_path"), weights(rs.getLong("id")))).list();
    }

    private List<MoodWeight> weights(long optionId) {
        return jdbc.sql("""
                SELECT mood.id, mood.name, mood.slug, mood.color, weight.score
                FROM quiz_option_mood_weights weight
                JOIN moods mood ON mood.id=weight.mood_id AND mood.is_active=1
                WHERE weight.option_id=:optionId
                ORDER BY weight.score DESC, mood.id
                """).param("optionId", optionId).query((rs, row) -> new MoodWeight(
                        rs.getLong("id"), rs.getString("name"), rs.getString("slug"),
                        rs.getString("color"), rs.getInt("score"))).list();
    }

    private StructureStatus validate(List<QuestionEditor> questions, List<PathChoice> paths) {
        var issues = new ArrayList<String>();
        long startingQuestions = questions.stream().filter(q -> q.order() == 1 && q.pathKey() == null).count();
        if (startingQuestions != 1) issues.add("Exactly one active universal starting question is required");
        var availablePaths = paths.stream().map(PathChoice::key).collect(java.util.stream.Collectors.toSet());
        if (!availablePaths.containsAll(PATH_KEYS)) issues.add("Quiz paths A, B, C and D are required");

        for (String path : PATH_KEYS) {
            for (int order = 2; order <= QUESTIONS_PER_QUIZ; order++) {
                int expectedOrder = order;
                long matches = questions.stream()
                        .filter(q -> expectedOrder == q.order() && path.equals(q.pathKey())).count();
                if (matches != 1) issues.add("Path " + path + " must contain exactly one Q" + order);
            }
        }
        for (var question : questions) {
            var keys = question.options().stream().map(OptionEditor::key).collect(java.util.stream.Collectors.toSet());
            if (question.options().size() != 4 || !keys.equals(OPTION_KEYS)) {
                issues.add(label(question) + " must have active options A, B, C and D");
            }
            if (question.options().stream().anyMatch(option -> option.weights().isEmpty())) {
                issues.add(label(question) + " has an option without a mood influence");
            }
            if (question.order() == 1) {
                var routes = question.options().stream().map(OptionEditor::routesToPath)
                        .collect(java.util.stream.Collectors.toSet());
                if (!routes.equals(new LinkedHashSet<>(PATH_KEYS))) {
                    issues.add("The starting question must route once each to paths A, B, C and D");
                }
            } else if (question.options().stream().anyMatch(option -> option.routesToPath() != null)) {
                issues.add(label(question) + " cannot change the selected path");
            }
        }
        return new StructureStatus(issues.isEmpty(), List.copyOf(issues));
    }

    private String label(QuestionEditor question) {
        return question.pathKey() == null ? "Starting question" : "Path " + question.pathKey() + " Q" + question.order();
    }
}
