package com.moodbuds.content;

import java.time.Instant;

public final class SiteContentDtos {
    private SiteContentDtos() {}

    public record AboutContent(
            String title,
            String headline,
            String story,
            String mission,
            Instant updatedAt) {}

    public record ContactContent(
            String title,
            String intro,
            String supportEmail,
            String supportPhone,
            String supportHours,
            String registeredAddress,
            Instant updatedAt) {}

    public record AdminContent(
            AboutContent about,
            ContactContent contact,
            String updatedBy,
            Instant updatedAt) {}

    public record AboutInput(String title, String headline, String story, String mission) {}

    public record ContactInput(
            String title,
            String intro,
            String supportEmail,
            String supportPhone,
            String supportHours,
            String registeredAddress) {}

    public record UpdateContentRequest(AboutInput about, ContactInput contact) {}
}
