package com.moodbuds.admin;

public final class MoodAdminDtos {
    private MoodAdminDtos() {}

    public record MoodAdminView(long id, String name, String slug, String emoji, String tagline,
                                String personalityTagline, String color, int displayOrder, boolean active,
                                long productCount, long quizWeightCount, long completedResultCount,
                                boolean bannerConfigured, String bannerImageUrl) {}

    public record CreateMoodRequest(String name, String slug, String emoji, String tagline,
                                    String personalityTagline, String color, Integer displayOrder,
                                    Boolean active) {}

    public record UpdateMoodRequest(String name, String emoji, String tagline,
                                    String personalityTagline, String color, Integer displayOrder) {}

    public record MoodStatusRequest(boolean active) {}
}
