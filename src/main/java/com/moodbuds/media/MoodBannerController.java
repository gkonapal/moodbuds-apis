package com.moodbuds.media;

import java.io.IOException;
import java.time.ZoneOffset;
import java.util.List;

import com.moodbuds.media.MoodBannerDtos.MoodBanner;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class MoodBannerController {
    private final MoodBannerService service;

    public MoodBannerController(MoodBannerService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/admin/mood-banners")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    List<MoodBanner> list() {
        return service.list();
    }

    @PutMapping(value = "/api/v1/admin/mood-banners/{moodId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    MoodBanner replace(@PathVariable long moodId, @RequestPart("file") MultipartFile file) {
        return service.replace(moodId, file);
    }

    @GetMapping("/api/v1/moods/{slug}/banner")
    ResponseEntity<InputStreamResource> content(@PathVariable String slug) throws IOException {
        var banner = service.content(slug);
        long lastModified = banner.updatedAt() == null ? 0
                : banner.updatedAt().toInstant(ZoneOffset.UTC).toEpochMilli();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(banner.contentType()))
                .cacheControl(CacheControl.noCache().mustRevalidate())
                .lastModified(lastModified)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + banner.filename() + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(banner.inputStream()));
    }
}
