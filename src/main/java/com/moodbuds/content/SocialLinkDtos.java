package com.moodbuds.content;

import java.time.Instant;
import java.util.List;

public final class SocialLinkDtos {
    private SocialLinkDtos() {}

    public record SocialLink(
            long id,
            String platform,
            String displayName,
            String url,
            String icon,
            int displayOrder,
            boolean enabled,
            Instant updatedAt) {}

    public record SocialLinkInput(long id, String url, boolean enabled) {}

    public record UpdateSocialLinksRequest(List<SocialLinkInput> links) {}
}
