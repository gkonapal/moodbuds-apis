package com.moodbuds.media;

import java.time.LocalDateTime;

public final class MoodBannerDtos {
    private MoodBannerDtos() {}

    public record MoodBanner(long moodId, String moodName, String moodSlug, String color,
                             boolean configured, String imagePath, String imageUrl,
                             String contentType, Long sizeBytes, Integer widthPx, Integer heightPx,
                             LocalDateTime updatedAt) {}
}
