package com.moodbuds.admin;

import static com.moodbuds.content.SocialLinkDtos.*;

import java.util.List;

import com.moodbuds.auth.CurrentAdmin;
import com.moodbuds.content.SocialLinkService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/content/social-links")
@PreAuthorize("hasAuthority('content.manage') or hasRole('SUPER_ADMIN')")
public class SocialLinkAdminController {
    private final SocialLinkService links;

    public SocialLinkAdminController(SocialLinkService links) {
        this.links = links;
    }

    @GetMapping
    List<SocialLink> list() { return links.adminLinks(); }

    @PutMapping
    List<SocialLink> update(@RequestBody UpdateSocialLinksRequest request, @AuthenticationPrincipal Jwt jwt) {
        return links.update(request, CurrentAdmin.id(jwt));
    }
}
