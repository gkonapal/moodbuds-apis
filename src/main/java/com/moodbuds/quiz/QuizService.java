package com.moodbuds.quiz;

import static com.moodbuds.quiz.api.QuizDtos.*;

import java.sql.Types;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.moodbuds.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuizService {
    public static final int TOTAL_QUESTIONS = 5;
    public static final int SESSION_VALIDITY_HOURS = 24;

    private final JdbcClient jdbc;
    private final QuizSessionTokenService tokenService;
    private final QuizScoringPolicy scoringPolicy;

    public QuizService(JdbcClient jdbc, QuizSessionTokenService tokenService, QuizScoringPolicy scoringPolicy) {
        this.jdbc = jdbc;
        this.tokenService = tokenService;
        this.scoringPolicy = scoringPolicy;
    }

    public QuizConfigResponse config() {
        var moods = jdbc.sql("""
                SELECT id, name, slug, tagline, color
                FROM moods
                WHERE is_active=1
                ORDER BY id
                """).query((rs, rowNum) -> new MoodSummary(
                        rs.getLong("id"), rs.getString("name"), rs.getString("slug"),
                        rs.getString("tagline"), rs.getString("color"))).list();
        return new QuizConfigResponse(TOTAL_QUESTIONS, 4, 1, SESSION_VALIDITY_HOURS, moods);
    }

    @Transactional
    public CreateSessionResponse createSession(HttpServletRequest request) {
        String id = UUID.randomUUID().toString();
        var token = tokenService.create();
        String ipAddress = clientIp(request);
        jdbc.sql("""
                INSERT INTO quiz_sessions(id, user_id, session_token, ip_address, result_mood_id,
                                          is_completed, started_at, completed_at)
                VALUES (:id, NULL, :tokenHash, :ipAddress, NULL, 0, UTC_TIMESTAMP(), NULL)
                """).param("id", id).param("tokenHash", token.hash()).param("ipAddress", ipAddress).update();
        var progress = progress(loadSession(id, token.raw(), false));
        return new CreateSessionResponse(id, token.raw(), progress.status(), progress.answeredQuestions(),
                progress.totalQuestions(), progress.progressPercent(), progress.startedAt(), progress.nextQuestion());
    }

    public SessionProgressResponse getProgress(String sessionId, String token) {
        return progress(loadSession(sessionId, token, false));
    }

    public QuestionResponse getNextQuestion(String sessionId, String token) {
        var progress = progress(loadSession(sessionId, token, false));
        if (progress.nextQuestion() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "QUIZ_HAS_NO_NEXT_QUESTION",
                    progress.status().equals("COMPLETED") ? "The quiz is already completed" : "All questions are answered; complete the quiz to calculate the result");
        }
        return progress.nextQuestion();
    }

    @Transactional
    public SessionProgressResponse answer(String sessionId, String token, AnswerRequest request) {
        if (request.questionId() == null || request.optionId() == null || request.questionId() <= 0 || request.optionId() <= 0) {
            throw ApiException.badRequest("INVALID_ANSWER", "questionId and optionId must be positive");
        }
        var session = loadSession(sessionId, token, true);
        if (session.completed()) {
            throw new ApiException(HttpStatus.CONFLICT, "QUIZ_ALREADY_COMPLETED", "A completed quiz cannot be changed");
        }

        var expectedBefore = expectedQuestions(session.id());
        var target = expectedBefore.stream().filter(question -> question.id() == request.questionId()).findFirst()
                .orElseThrow(() -> ApiException.badRequest("QUESTION_NOT_IN_QUIZ_PATH", "The question is not part of this quiz path"));
        Set<Long> answeredBefore = answeredQuestionIds(session.id());
        int nextOrder = expectedBefore.stream().filter(question -> !answeredBefore.contains(question.id()))
                .mapToInt(ExpectedQuestion::order).findFirst().orElse(TOTAL_QUESTIONS + 1);
        if (!answeredBefore.contains(target.id()) && target.order() != nextOrder) {
            throw ApiException.badRequest("QUESTION_OUT_OF_ORDER", "Answer the current question before continuing");
        }

        var option = jdbc.sql("""
                SELECT id, routes_to_path
                FROM quiz_options
                WHERE id=:optionId AND question_id=:questionId AND is_active=1
                """).param("optionId", request.optionId()).param("questionId", request.questionId())
                .query((rs, rowNum) -> new SelectedOption(rs.getLong("id"), rs.getString("routes_to_path")))
                .optional().orElseThrow(() -> ApiException.badRequest("INVALID_OPTION", "The option does not belong to the selected active question"));

        jdbc.sql("""
                DELETE answer
                FROM quiz_session_answers answer
                JOIN quiz_questions question ON question.id=answer.question_id
                WHERE answer.quiz_session_id=:sessionId AND question.question_order>=:questionOrder
                """).param("sessionId", session.id()).param("questionOrder", target.order()).update();
        jdbc.sql("""
                INSERT INTO quiz_session_answers(quiz_session_id, question_id, option_id, answered_at)
                VALUES (:sessionId, :questionId, :optionId, UTC_TIMESTAMP())
                """).param("sessionId", session.id()).param("questionId", target.id())
                .param("optionId", option.id()).update();
        return progress(loadSession(sessionId, token, false));
    }

    @Transactional
    public QuizResultResponse complete(String sessionId, String token) {
        var session = loadSession(sessionId, token, true);
        if (session.completed()) return result(session);

        var expected = expectedQuestions(session.id());
        Set<Long> answered = answeredQuestionIds(session.id());
        if (expected.size() != TOTAL_QUESTIONS || !answered.containsAll(expected.stream().map(ExpectedQuestion::id).toList())) {
            throw new ApiException(HttpStatus.CONFLICT, "QUIZ_INCOMPLETE",
                    "All " + TOTAL_QUESTIONS + " questions must be answered before completion");
        }

        var ranked = rankedScores(session.id());
        if (ranked.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "QUIZ_SCORING_UNAVAILABLE", "No active mood scoring configuration is available");
        }
        long winnerId = ranked.getFirst().moodId();
        jdbc.sql("""
                UPDATE quiz_sessions
                SET result_mood_id=:moodId, is_completed=1, completed_at=UTC_TIMESTAMP()
                WHERE id=:sessionId
                """).param("moodId", winnerId).param("sessionId", session.id()).update();
        return result(loadSession(sessionId, token, false));
    }

    public QuizResultResponse getResult(String sessionId, String token) {
        var session = loadSession(sessionId, token, false);
        if (!session.completed()) {
            throw new ApiException(HttpStatus.CONFLICT, "QUIZ_NOT_COMPLETED", "Complete the quiz before requesting its result");
        }
        return result(session);
    }

    private SessionProgressResponse progress(QuizSession session) {
        var expected = expectedQuestions(session.id());
        Set<Long> answered = answeredQuestionIds(session.id());
        QuestionResponse next = expected.stream().filter(question -> !answered.contains(question.id()))
                .findFirst().map(this::questionResponse).orElse(null);
        int answeredCount = (int) expected.stream().filter(question -> answered.contains(question.id())).count();
        String status = session.completed() ? "COMPLETED" : next == null ? "READY_TO_COMPLETE" : "IN_PROGRESS";
        return new SessionProgressResponse(session.id(), status, answeredCount, TOTAL_QUESTIONS,
                answeredCount * 100 / TOTAL_QUESTIONS, session.startedAt(), session.completedAt(), next);
    }

    private List<ExpectedQuestion> expectedQuestions(String sessionId) {
        String path = jdbc.sql("""
                SELECT qo.routes_to_path
                FROM quiz_session_answers qsa
                JOIN quiz_questions qq ON qq.id=qsa.question_id
                JOIN quiz_options qo ON qo.id=qsa.option_id
                WHERE qsa.quiz_session_id=:sessionId AND qq.question_order=1
                """).param("sessionId", sessionId).query(String.class).optional().orElse(null);

        return jdbc.sql("""
                SELECT id, question_order, question_text
                FROM quiz_questions
                WHERE is_active=1 AND (
                    (question_order=1 AND path_key IS NULL)
                    OR (:path IS NOT NULL AND question_order BETWEEN 2 AND 5 AND path_key=:path)
                )
                ORDER BY question_order, id
                """).param("path", path, Types.VARCHAR)
                .query((rs, rowNum) -> new ExpectedQuestion(
                        rs.getLong("id"), rs.getInt("question_order"), rs.getString("question_text"))).list();
    }

    private QuestionResponse questionResponse(ExpectedQuestion question) {
        var options = jdbc.sql("""
                SELECT id, option_key, option_text, sort_order
                FROM quiz_options
                WHERE question_id=:questionId AND is_active=1
                ORDER BY sort_order, id
                """).param("questionId", question.id())
                .query((rs, rowNum) -> new OptionResponse(
                        rs.getLong("id"), rs.getString("option_key"), rs.getString("option_text"), rs.getInt("sort_order"))).list();
        return new QuestionResponse(question.id(), question.order(), question.text(), options);
    }

    private Set<Long> answeredQuestionIds(String sessionId) {
        return new LinkedHashSet<>(jdbc.sql("""
                SELECT question_id FROM quiz_session_answers WHERE quiz_session_id=:sessionId
                """).param("sessionId", sessionId).query(Long.class).list());
    }

    private List<QuizScoringPolicy.ScoreCandidate> rankedScores(String sessionId) {
        var candidates = jdbc.sql("""
                SELECT mood.id, mood.name, mood.slug, mood.tagline, mood.personality_tagline,
                       CASE WHEN mood.banner_image_path IS NOT NULL
                            THEN CONCAT('/api/v1/moods/',mood.slug,'/banner')
                            ELSE mood.banner_image_url END AS banner_image_url,
                       mood.color,
                       COALESCE(SUM(CASE WHEN answer.id IS NOT NULL THEN weight.score ELSE 0 END),0) total_score,
                       COALESCE(SUM(CASE WHEN answer.id IS NOT NULL AND question.question_order=:finalQuestionOrder THEN weight.score ELSE 0 END),0) final_score
                FROM moods mood
                LEFT JOIN quiz_option_mood_weights weight ON weight.mood_id=mood.id
                LEFT JOIN quiz_session_answers answer
                  ON answer.option_id=weight.option_id AND answer.quiz_session_id=:sessionId
                LEFT JOIN quiz_questions question ON question.id=answer.question_id
                WHERE mood.is_active=1
                   OR mood.id=(SELECT result_mood_id FROM quiz_sessions WHERE id=:sessionId AND is_completed=1)
                GROUP BY mood.id, mood.name, mood.slug, mood.tagline, mood.personality_tagline,
                         mood.banner_image_url, mood.banner_image_path, mood.color
                """).param("sessionId", sessionId).param("finalQuestionOrder", TOTAL_QUESTIONS)
                .query((rs, rowNum) -> new QuizScoringPolicy.ScoreCandidate(
                        rs.getLong("id"), rs.getString("name"), rs.getString("slug"),
                        rs.getString("tagline"), rs.getString("personality_tagline"),
                        rs.getString("banner_image_url"), rs.getString("color"),
                        rs.getInt("total_score"), rs.getInt("final_score"))).list();
        return scoringPolicy.rank(candidates);
    }

    private QuizResultResponse result(QuizSession session) {
        var ranked = rankedScores(session.id());
        var winner = ranked.stream().filter(score -> score.moodId() == session.resultMoodId()).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "RESULT_MOOD_UNAVAILABLE", "The result mood is no longer active"));
        var mood = new MoodResult(winner.moodId(), winner.name(), winner.slug(), winner.tagline(),
                winner.personalityTagline(), winner.bannerImageUrl(), winner.color());
        var breakdown = ranked.stream().map(score -> new MoodScore(
                score.moodId(), score.name(), score.slug(), score.totalScore())).toList();
        return new QuizResultResponse(session.id(), "COMPLETED", session.completedAt(), mood,
                breakdown, recommendations(winner.moodId()));
    }

    private List<RecommendedProduct> recommendations(long moodId) {
        return jdbc.sql("""
                SELECT product.id, product.sku, product.slug, product.name, product.price, product.discount_price,
                       (SELECT image.image_url FROM product_images image
                        WHERE image.product_id=product.id ORDER BY image.is_primary DESC, image.id LIMIT 1) primary_image_url,
                       (SELECT COALESCE(SUM(size.stock_quantity),0) FROM product_sizes size
                        WHERE size.product_id=product.id AND size.is_available=1) available_stock
                FROM products product
                JOIN product_mood_tags tag ON tag.product_id=product.id
                WHERE tag.mood_id=:moodId AND product.is_active=1
                  AND EXISTS (SELECT 1 FROM product_sizes size
                              WHERE size.product_id=product.id AND size.is_available=1 AND size.stock_quantity>0)
                ORDER BY product.is_featured DESC, product.is_best_seller DESC,
                         product.is_new_arrival DESC, product.id DESC
                LIMIT 12
                """).param("moodId", moodId)
                .query((rs, rowNum) -> new RecommendedProduct(
                        rs.getLong("id"), rs.getString("sku"), rs.getString("slug"), rs.getString("name"),
                        rs.getLong("price"), nullableLong(rs, "discount_price"),
                        rs.getString("primary_image_url"), rs.getLong("available_stock"))).list();
    }

    private QuizSession loadSession(String sessionId, String rawToken, boolean forUpdate) {
        String sql = "SELECT id, session_token, result_mood_id, is_completed, started_at, completed_at " +
                "FROM quiz_sessions WHERE id=:sessionId" + (forUpdate ? " FOR UPDATE" : "");
        var session = jdbc.sql(sql).param("sessionId", sessionId)
                .query((rs, rowNum) -> new QuizSession(
                        rs.getString("id"), rs.getString("session_token"),
                        nullableLong(rs, "result_mood_id"), rs.getBoolean("is_completed"),
                        rs.getObject("started_at", LocalDateTime.class),
                        rs.getObject("completed_at", LocalDateTime.class))).optional()
                .orElseThrow(() -> ApiException.notFound("Quiz session"));
        if (!tokenService.matches(rawToken, session.tokenHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_QUIZ_SESSION_TOKEN", "The quiz session token is invalid");
        }
        if (!session.completed() && session.startedAt().isBefore(LocalDateTime.now(ZoneOffset.UTC).minusHours(SESSION_VALIDITY_HOURS))) {
            throw new ApiException(HttpStatus.GONE, "QUIZ_SESSION_EXPIRED", "The quiz session has expired; start a new quiz");
        }
        return session;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
        return ip == null ? null : ip.substring(0, Math.min(ip.length(), 45));
    }

    private static Long nullableLong(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private record QuizSession(String id, String tokenHash, Long resultMoodId, boolean completed,
                               LocalDateTime startedAt, LocalDateTime completedAt) {}
    private record ExpectedQuestion(long id, int order, String text) {}
    private record SelectedOption(long id, String routesToPath) {}
}
