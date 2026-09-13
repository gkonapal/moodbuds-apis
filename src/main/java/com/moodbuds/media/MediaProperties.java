package com.moodbuds.media;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "moodbuds.media")
public record MediaProperties(Path root, long maxImageSizeBytes, int maxImagesPerUpload) {
    public MediaProperties {
        root = (root == null ? Path.of("storage", "media") : root).toAbsolutePath().normalize();
        maxImageSizeBytes = maxImageSizeBytes <= 0 ? 5L * 1024 * 1024 : maxImageSizeBytes;
        maxImagesPerUpload = maxImagesPerUpload <= 0 ? 10 : Math.min(maxImagesPerUpload, 25);
    }
}
