package com.moodbuds.content;

import static com.moodbuds.content.SiteContentDtos.*;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/content")
public class SiteContentController {
    private final SiteContentService content;

    public SiteContentController(SiteContentService content) {
        this.content = content;
    }

    @GetMapping("/about-us")
    AboutContent about() { return content.about(); }

    @GetMapping("/contact-us")
    ContactContent contact() { return content.contact(); }
}
