package com.moodbuds.admin;

import static com.moodbuds.content.SiteContentDtos.*;

import com.moodbuds.auth.CurrentAdmin;
import com.moodbuds.content.SiteContentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/content/about-contact")
@PreAuthorize("hasAuthority('content.manage') or hasRole('SUPER_ADMIN')")
public class SiteContentAdminController {
    private final SiteContentService content;

    public SiteContentAdminController(SiteContentService content) {
        this.content = content;
    }

    @GetMapping
    AdminContent get() { return content.admin(); }

    @PutMapping
    AdminContent update(@RequestBody UpdateContentRequest request, @AuthenticationPrincipal Jwt jwt) {
        return content.update(request, CurrentAdmin.id(jwt));
    }
}
