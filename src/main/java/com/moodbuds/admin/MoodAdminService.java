package com.moodbuds.admin;

import static com.moodbuds.admin.MoodAdminDtos.*;

import java.util.List;
import java.util.Map;

import com.moodbuds.audit.AuditService;
import com.moodbuds.common.ApiException;
import com.moodbuds.common.SlugService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MoodAdminService {
    private final JdbcClient jdbc;
    private final SlugService slugService;
    private final AuditService audit;

    public MoodAdminService(JdbcClient jdbc, SlugService slugService, AuditService audit) {
        this.jdbc = jdbc;
        this.slugService = slugService;
        this.audit = audit;
    }

    public List<MoodAdminView> list() {
        return jdbc.sql("""
                SELECT m.id,m.name,m.slug,m.emoji,m.tagline,m.personality_tagline,m.color,
                       m.display_order,m.is_active,
                       COUNT(DISTINCT pmt.product_id) product_count,
                       COUNT(DISTINCT qmw.option_id) quiz_weight_count,
                       COUNT(DISTINCT CASE WHEN qs.is_completed=1 THEN qs.id END) completed_result_count,
                       (m.banner_image_path IS NOT NULL) banner_configured,
                       CASE WHEN m.banner_image_path IS NOT NULL
                            THEN CONCAT('/api/v1/moods/',m.slug,'/banner')
                            ELSE m.banner_image_url END banner_image_url
                FROM moods m
                LEFT JOIN product_mood_tags pmt ON pmt.mood_id=m.id
                LEFT JOIN quiz_option_mood_weights qmw ON qmw.mood_id=m.id
                LEFT JOIN quiz_sessions qs ON qs.result_mood_id=m.id
                GROUP BY m.id,m.name,m.slug,m.emoji,m.tagline,m.personality_tagline,m.color,
                         m.display_order,m.is_active,m.banner_image_path,m.banner_image_url
                ORDER BY m.display_order,m.id
                """).query((rs, row) -> new MoodAdminView(
                rs.getLong("id"), rs.getString("name"), rs.getString("slug"), rs.getString("emoji"),
                rs.getString("tagline"), rs.getString("personality_tagline"), rs.getString("color"),
                rs.getInt("display_order"), rs.getBoolean("is_active"), rs.getLong("product_count"),
                rs.getLong("quiz_weight_count"), rs.getLong("completed_result_count"),
                rs.getBoolean("banner_configured"), rs.getString("banner_image_url"))).list();
    }

    public MoodAdminView get(long id) {
        return list().stream().filter(mood -> mood.id() == id).findFirst()
                .orElseThrow(() -> ApiException.notFound("Mood"));
    }

    @Transactional
    public MoodAdminView create(CreateMoodRequest request, long actorId) {
        String name = required(request.name(), "Mood name", 100);
        String emoji = required(request.emoji(), "Emoji", 20);
        String color = color(request.color());
        int displayOrder = request.displayOrder() == null ? nextDisplayOrder() : order(request.displayOrder());
        String slug = slugService.uniqueSlug("moods", request.slug(), name, null);
        jdbc.sql("""
                INSERT INTO moods(name,slug,emoji,tagline,personality_tagline,color,display_order,is_active)
                VALUES (:name,:slug,:emoji,:tagline,:personality,:color,:displayOrder,:active)
                """).param("name", name).param("slug", slug).param("emoji", emoji)
                .param("tagline", optional(request.tagline(), 300))
                .param("personality", optional(request.personalityTagline(), 300))
                .param("color", color).param("displayOrder", displayOrder)
                .param("active", request.active() == null || request.active()).update();
        long id = jdbc.sql("SELECT id FROM moods WHERE slug=:slug").param("slug", slug).query(Long.class).single();
        var created = get(id);
        audit.record(actorId, "moods.created", "moods", id, null, created);
        return created;
    }

    @Transactional
    public MoodAdminView update(long id, UpdateMoodRequest request, long actorId) {
        var old = get(id);
        String name = required(request.name(), "Mood name", 100);
        String emoji = required(request.emoji(), "Emoji", 20);
        jdbc.sql("""
                UPDATE moods SET name=:name,emoji=:emoji,tagline=:tagline,
                                 personality_tagline=:personality,color=:color,display_order=:displayOrder
                WHERE id=:id
                """).param("name", name).param("emoji", emoji)
                .param("tagline", optional(request.tagline(), 300))
                .param("personality", optional(request.personalityTagline(), 300))
                .param("color", color(request.color())).param("displayOrder", order(request.displayOrder()))
                .param("id", id).update();
        var updated = get(id);
        audit.record(actorId, "moods.updated", "moods", id, old, updated);
        return updated;
    }

    @Transactional
    public MoodAdminView setStatus(long id, boolean active, long actorId) {
        var old = get(id);
        if (old.active() == active) return old;
        if (!active) ensureQuizRemainsScoreable(id);
        jdbc.sql("UPDATE moods SET is_active=:active WHERE id=:id")
                .param("active", active).param("id", id).update();
        var updated = get(id);
        audit.record(actorId, active ? "moods.activated" : "moods.deactivated", "moods", id,
                old, Map.of("active", active));
        return updated;
    }

    private void ensureQuizRemainsScoreable(long moodId) {
        long strandedOptions = jdbc.sql("""
                SELECT COUNT(*) FROM quiz_options option_row
                JOIN quiz_questions question ON question.id=option_row.question_id AND question.is_active=1
                WHERE option_row.is_active=1
                  AND EXISTS (SELECT 1 FROM quiz_option_mood_weights target
                              WHERE target.option_id=option_row.id AND target.mood_id=:moodId)
                  AND NOT EXISTS (SELECT 1 FROM quiz_option_mood_weights other_weight
                                  JOIN moods other_mood ON other_mood.id=other_weight.mood_id
                                                       AND other_mood.is_active=1
                                  WHERE other_weight.option_id=option_row.id
                                    AND other_weight.mood_id<>:moodId)
                """).param("moodId", moodId).query(Long.class).single();
        if (strandedOptions > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "MOOD_REQUIRED_BY_QUIZ",
                    "This mood is the only active scoring mood for " + strandedOptions
                            + " quiz answer option(s). Add another mood weight before deactivating it.");
        }
    }

    private int nextDisplayOrder() {
        return jdbc.sql("SELECT COALESCE(MAX(display_order),0)+1 FROM moods").query(Integer.class).single();
    }

    private int order(Integer value) {
        if (value == null || value < 0 || value > 10000) {
            throw ApiException.badRequest("INVALID_DISPLAY_ORDER", "Display order must be between 0 and 10000");
        }
        return value;
    }

    private String color(String value) {
        String result = value == null || value.isBlank() ? "#6B7280" : value.trim();
        if (!result.matches("#[0-9a-fA-F]{6}")) {
            throw ApiException.badRequest("INVALID_MOOD_COLOR", "Colour must be a six-digit hex value such as #6B7280");
        }
        return result.toUpperCase();
    }

    private String required(String value, String label, int maxLength) {
        String result = value == null ? "" : value.trim();
        if (result.isEmpty() || result.length() > maxLength) {
            throw ApiException.badRequest("INVALID_MOOD", label + " is required and must be at most " + maxLength + " characters");
        }
        return result;
    }

    private String optional(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        String result = value.trim();
        if (result.length() > maxLength) {
            throw ApiException.badRequest("INVALID_MOOD", "Mood text must be at most " + maxLength + " characters");
        }
        return result;
    }
}
