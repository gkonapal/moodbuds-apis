package com.moodbuds.content;

import static com.moodbuds.content.SiteContentDtos.*;

import java.sql.Timestamp;
import java.util.regex.Pattern;

import com.moodbuds.audit.AuditService;
import com.moodbuds.common.ApiException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SiteContentService {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private final JdbcClient jdbc;
    private final AuditService audit;

    public SiteContentService(JdbcClient jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public AboutContent about() {
        var row = row();
        return new AboutContent(row.aboutTitle(), row.aboutHeadline(), row.aboutStory(), row.aboutMission(), row.updatedAt().toInstant());
    }

    @Transactional(readOnly = true)
    public ContactContent contact() {
        var row = row();
        return new ContactContent(row.contactTitle(), row.contactIntro(), row.supportEmail(), row.supportPhone(),
                row.supportHours(), row.registeredAddress(), row.updatedAt().toInstant());
    }

    @Transactional(readOnly = true)
    public AdminContent admin() {
        var row = row();
        var updatedAt = row.updatedAt().toInstant();
        return new AdminContent(
                new AboutContent(row.aboutTitle(), row.aboutHeadline(), row.aboutStory(), row.aboutMission(), updatedAt),
                new ContactContent(row.contactTitle(), row.contactIntro(), row.supportEmail(), row.supportPhone(),
                        row.supportHours(), row.registeredAddress(), updatedAt),
                row.updatedBy(), updatedAt);
    }

    @Transactional
    public AdminContent update(UpdateContentRequest request, long actorId) {
        if (request == null || request.about() == null || request.contact() == null) {
            throw ApiException.badRequest("INVALID_SITE_CONTENT", "About and Contact content are required");
        }
        var old = admin();
        var about = request.about();
        var contact = request.contact();
        String email = required(contact.supportEmail(), "Support email", 254).toLowerCase();
        if (!EMAIL.matcher(email).matches()) {
            throw ApiException.badRequest("INVALID_SUPPORT_EMAIL", "Enter a valid support email address");
        }
        jdbc.sql("""
                UPDATE site_content SET
                    about_title=:aboutTitle,about_headline=:aboutHeadline,about_story=:aboutStory,
                    about_mission=:aboutMission,contact_title=:contactTitle,contact_intro=:contactIntro,
                    support_email=:supportEmail,support_phone=:supportPhone,support_hours=:supportHours,
                    registered_address=:registeredAddress,updated_by=:actor,updated_at=UTC_TIMESTAMP()
                WHERE id=1
                """)
                .param("aboutTitle", required(about.title(), "About page title", 160))
                .param("aboutHeadline", required(about.headline(), "About headline", 300))
                .param("aboutStory", required(about.story(), "About story", 10_000))
                .param("aboutMission", required(about.mission(), "About mission", 10_000))
                .param("contactTitle", required(contact.title(), "Contact page title", 160))
                .param("contactIntro", required(contact.intro(), "Contact introduction", 500))
                .param("supportEmail", email)
                .param("supportPhone", required(contact.supportPhone(), "Support phone", 60))
                .param("supportHours", required(contact.supportHours(), "Support hours", 200))
                .param("registeredAddress", required(contact.registeredAddress(), "Registered address", 2_000))
                .param("actor", actorId)
                .update();
        var updated = admin();
        audit.record(actorId, "content.about_contact.updated", "site_content", 1, old, updated);
        return updated;
    }

    private ContentRow row() {
        return jdbc.sql("""
                SELECT sc.about_title,sc.about_headline,sc.about_story,sc.about_mission,
                       sc.contact_title,sc.contact_intro,sc.support_email,sc.support_phone,
                       sc.support_hours,sc.registered_address,sc.updated_at,au.username updated_by
                FROM site_content sc
                LEFT JOIN admin_users au ON au.id=sc.updated_by
                WHERE sc.id=1
                """).query((rs, number) -> new ContentRow(
                rs.getString("about_title"), rs.getString("about_headline"), rs.getString("about_story"),
                rs.getString("about_mission"), rs.getString("contact_title"), rs.getString("contact_intro"),
                rs.getString("support_email"), rs.getString("support_phone"), rs.getString("support_hours"),
                rs.getString("registered_address"), rs.getTimestamp("updated_at"), rs.getString("updated_by")))
                .optional().orElseThrow(() -> ApiException.notFound("Site content"));
    }

    private static String required(String value, String label, int maxLength) {
        String result = value == null ? "" : value.trim();
        if (result.isEmpty()) throw ApiException.badRequest("INVALID_SITE_CONTENT", label + " is required");
        if (result.length() > maxLength) {
            throw ApiException.badRequest("INVALID_SITE_CONTENT", label + " must be at most " + maxLength + " characters");
        }
        return result;
    }

    private record ContentRow(
            String aboutTitle,String aboutHeadline,String aboutStory,String aboutMission,
            String contactTitle,String contactIntro,String supportEmail,String supportPhone,
            String supportHours,String registeredAddress,Timestamp updatedAt,String updatedBy) {}
}
