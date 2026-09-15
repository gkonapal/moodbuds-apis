package com.moodbuds.media;

import static com.moodbuds.media.MoodBannerDtos.MoodBanner;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;

import com.moodbuds.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MoodBannerService {
    private final MediaProperties properties;
    private final JdbcClient jdbc;

    public MoodBannerService(MediaProperties properties, JdbcClient jdbc) {
        this.properties = properties;
        this.jdbc = jdbc;
    }

    public List<MoodBanner> list() {
        return jdbc.sql("""
                SELECT id,name,slug,color,banner_image_path,banner_image_content_type,
                       banner_image_size_bytes,banner_image_width_px,banner_image_height_px,
                       banner_image_updated_at
                FROM moods
                ORDER BY id
                """).query((rs, row) -> new MoodBanner(
                        rs.getLong("id"), rs.getString("name"), rs.getString("slug"), rs.getString("color"),
                        rs.getString("banner_image_path") != null, rs.getString("banner_image_path"),
                        rs.getString("banner_image_path") == null ? null : publicUrl(rs.getString("slug")),
                        rs.getString("banner_image_content_type"), nullableLong(rs.getObject("banner_image_size_bytes")),
                        nullableInteger(rs.getObject("banner_image_width_px")),
                        nullableInteger(rs.getObject("banner_image_height_px")),
                        rs.getObject("banner_image_updated_at", LocalDateTime.class))).list();
    }

    public synchronized MoodBanner replace(long moodId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("BANNER_REQUIRED", "Choose a banner image to upload");
        }
        if (file.getSize() > properties.maxImageSizeBytes()) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "IMAGE_TOO_LARGE",
                    "Banner image exceeds the configured size limit");
        }

        MoodRow mood = jdbc.sql("""
                SELECT id,name,slug,color,banner_image_path
                FROM moods WHERE id=:id
                """).param("id", moodId).query((rs, row) -> new MoodRow(
                        rs.getLong("id"), rs.getString("name"), rs.getString("slug"),
                        rs.getString("color"), rs.getString("banner_image_path")))
                .optional().orElseThrow(() -> ApiException.notFound("Mood"));

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw storageFailure();
        }
        var detected = ImageMetadata.detect(bytes);
        Path directory = safeResolve("moods");
        String relativePath = "moods/" + mood.slug() + "." + detected.extension();
        Path target = safeResolve(relativePath);
        Path oldTarget = mood.imagePath() == null ? null : safeResolve(mood.imagePath());
        Path temporary = null;
        Path backup = null;

        try {
            Files.createDirectories(directory);
            temporary = Files.createTempFile(directory, mood.slug() + "-upload-", ".tmp");
            Files.write(temporary, bytes);

            if (target.equals(oldTarget) && Files.exists(target)) {
                backup = Files.createTempFile(directory, mood.slug() + "-backup-", ".tmp");
                move(target, backup, true);
            }
            move(temporary, target, true);
            temporary = null;

            try {
                jdbc.sql("""
                        UPDATE moods
                        SET banner_image_path=:path,
                            banner_image_content_type=:contentType,
                            banner_image_size_bytes=:size,
                            banner_image_width_px=:width,
                            banner_image_height_px=:height,
                            banner_image_updated_at=UTC_TIMESTAMP()
                        WHERE id=:id
                        """).param("path", relativePath).param("contentType", detected.contentType())
                        .param("size", bytes.length).param("width", detected.width()).param("height", detected.height())
                        .param("id", moodId).update();
            } catch (RuntimeException exception) {
                Files.deleteIfExists(target);
                if (backup != null && Files.exists(backup)) move(backup, target, true);
                throw exception;
            }

            if (backup != null) Files.deleteIfExists(backup);
            if (oldTarget != null && !oldTarget.equals(target)) Files.deleteIfExists(oldTarget);
            return get(moodId);
        } catch (IOException exception) {
            throw storageFailure();
        } finally {
            deleteQuietly(temporary);
            deleteQuietly(backup);
        }
    }

    public StoredBanner content(String slug) {
        var stored = jdbc.sql("""
                SELECT slug,banner_image_path,banner_image_content_type,banner_image_updated_at
                FROM moods
                WHERE slug=:slug AND is_active=1 AND banner_image_path IS NOT NULL
                """).param("slug", slug).query((rs, row) -> new StoredBanner(
                        safeResolve(rs.getString("banner_image_path")), rs.getString("banner_image_content_type"),
                        rs.getString("slug"), rs.getObject("banner_image_updated_at", LocalDateTime.class)))
                .optional().orElseThrow(() -> ApiException.notFound("Mood banner"));
        if (!Files.isRegularFile(stored.path())) throw ApiException.notFound("Mood banner");
        return stored;
    }

    private MoodBanner get(long moodId) {
        return list().stream().filter(banner -> banner.moodId() == moodId).findFirst()
                .orElseThrow(() -> ApiException.notFound("Mood"));
    }

    private Path safeResolve(String relativePath) {
        Path path = properties.root().resolve(relativePath.replace('/', java.io.File.separatorChar)).normalize();
        if (!path.startsWith(properties.root())) throw new IllegalStateException("Invalid mood banner path");
        return path;
    }

    private void move(Path source, Path target, boolean replace) throws IOException {
        var options = replace
                ? new StandardCopyOption[]{StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING}
                : new StandardCopyOption[]{StandardCopyOption.ATOMIC_MOVE};
        try {
            Files.move(source, target, options);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) return;
        try { Files.deleteIfExists(path); } catch (IOException ignored) { }
    }

    private static Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static Integer nullableInteger(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }

    private static String publicUrl(String slug) {
        return "/api/v1/moods/" + slug + "/banner";
    }

    private ApiException storageFailure() {
        return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "MOOD_BANNER_STORAGE_ERROR",
                "The mood banner could not be stored");
    }

    private record MoodRow(long id, String name, String slug, String color, String imagePath) {}

    public record StoredBanner(Path path, String contentType, String slug, LocalDateTime updatedAt) {
        public InputStream inputStream() throws IOException { return Files.newInputStream(path); }
        public String filename() { return path.getFileName().toString(); }
    }
}
