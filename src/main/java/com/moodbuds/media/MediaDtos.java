package com.moodbuds.media;

import java.time.LocalDateTime;

public final class MediaDtos {
    private MediaDtos() {}

    public record MediaAsset(long id, String url, String originalFilename, String contentType,
                             long sizeBytes, Integer widthPx, Integer heightPx, LocalDateTime createdAt) {}
}
