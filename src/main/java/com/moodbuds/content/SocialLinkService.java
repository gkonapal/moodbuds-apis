package com.moodbuds.content;

import static com.moodbuds.content.SocialLinkDtos.*;

import java.net.URI;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.List;

import com.moodbuds.audit.AuditService;
import com.moodbuds.common.ApiException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SocialLinkService {
    private final JdbcClient jdbc;
    private final AuditService audit;

    public SocialLinkService(JdbcClient jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<SocialLink> publicLinks() {
        return list(true);
    }

    @Transactional(readOnly = true)
    public List<SocialLink> adminLinks() {
        return list(false);
    }

    @Transactional
    public List<SocialLink> update(UpdateSocialLinksRequest request, long actorId) {
        if (request == null || request.links() == null || request.links().isEmpty()) {
            throw ApiException.badRequest("INVALID_SOCIAL_LINKS", "At least one social link is required");
        }
        var existing = adminLinks();
        if (request.links().size() != existing.size()) {
            throw ApiException.badRequest("INVALID_SOCIAL_LINKS", "Submit every configured social platform");
        }
        var validIds = existing.stream().map(SocialLink::id).collect(java.util.stream.Collectors.toSet());
        var submittedIds = new HashSet<Long>();
        for (var input : request.links()) {
            if (!validIds.contains(input.id()) || !submittedIds.add(input.id())) {
                throw ApiException.badRequest("INVALID_SOCIAL_LINKS", "Social link identifiers must be unique and valid");
            }
            String url = validUrl(input.url());
            jdbc.sql("""
                    UPDATE social_links
                    SET profile_url=:url,is_enabled=:enabled,updated_by=:actor,updated_at=UTC_TIMESTAMP()
                    WHERE id=:id
                    """).param("url", url).param("enabled", input.enabled()).param("actor", actorId)
                    .param("id", input.id()).update();
        }
        var updated = adminLinks();
        audit.record(actorId, "content.social_links.updated", "social_links", "all", existing, updated);
        return updated;
    }

    private List<SocialLink> list(boolean enabledOnly) {
        String sql = """
                SELECT id,platform_key,display_name,profile_url,icon_key,display_order,is_enabled,updated_at
                FROM social_links
                """ + (enabledOnly ? " WHERE is_enabled=1" : "") + " ORDER BY display_order,id";
        return jdbc.sql(sql).query((rs, row) -> link(
                rs.getLong("id"), rs.getString("platform_key"), rs.getString("display_name"),
                rs.getString("profile_url"), rs.getString("icon_key"), rs.getInt("display_order"),
                rs.getBoolean("is_enabled"), rs.getTimestamp("updated_at"))).list();
    }

    private static SocialLink link(long id, String platform, String displayName, String url, String icon,
                                   int displayOrder, boolean enabled, Timestamp updatedAt) {
        return new SocialLink(id, platform, displayName, url, icon, displayOrder, enabled, updatedAt.toInstant());
    }

    private static String validUrl(String value) {
        String url = value == null ? "" : value.trim();
        if (url.isEmpty() || url.length() > 2048) {
            throw ApiException.badRequest("INVALID_SOCIAL_URL", "Each social profile needs a valid URL");
        }
        try {
            URI uri = URI.create(url);
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw ApiException.badRequest("INVALID_SOCIAL_URL", "Social profile URLs must use http or https");
        }
        return url;
    }
}
