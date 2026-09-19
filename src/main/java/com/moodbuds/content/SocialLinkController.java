package com.moodbuds.content;

import static com.moodbuds.content.SocialLinkDtos.*;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/content/social-links")
public class SocialLinkController {
    private final SocialLinkService links;

    public SocialLinkController(SocialLinkService links) {
        this.links = links;
    }

    @GetMapping
    List<SocialLink> list() { return links.publicLinks(); }
}
