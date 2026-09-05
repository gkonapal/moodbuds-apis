package com.moodbuds.common;

import java.text.Normalizer;
import java.util.Locale;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class SlugService {
    private final JdbcClient jdbc;

    public SlugService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public String uniqueSlug(String table, String requested, String name, Long excludedId) {
        if (!java.util.Set.of("products", "categories", "subcategories", "moods").contains(table)) {
            throw new IllegalArgumentException("Unsupported slug table");
        }
        String base = normalize(requested == null || requested.isBlank() ? name : requested);
        if (base.isBlank()) {
            throw ApiException.badRequest("INVALID_SLUG", "A name or slug containing letters or numbers is required");
        }
        String candidate = base;
        int suffix = 2;
        while (exists(table, candidate, excludedId)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private boolean exists(String table, String slug, Long excludedId) {
        String sql = "SELECT COUNT(*) FROM " + table + " WHERE slug = :slug" + (excludedId == null ? "" : " AND id <> :id");
        var query = jdbc.sql(sql).param("slug", slug);
        if (excludedId != null) query = query.param("id", excludedId);
        return query.query(Integer.class).single() > 0;
    }

    private String normalize(String input) {
        return Normalizer.normalize(input, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
    }
}
