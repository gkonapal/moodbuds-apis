package com.moodbuds.admin;

import static com.moodbuds.admin.MoodAdminDtos.*;

import java.net.URI;
import java.util.List;

import com.moodbuds.auth.CurrentAdmin;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/moods")
public class MoodAdminController {
    private final MoodAdminService moods;

    public MoodAdminController(MoodAdminService moods) {
        this.moods = moods;
    }

    @GetMapping
    @PreAuthorize("@adminResourceAccess.canRead('moods', authentication)")
    List<MoodAdminView> list() { return moods.list(); }

    @GetMapping("/{id}")
    @PreAuthorize("@adminResourceAccess.canRead('moods', authentication)")
    MoodAdminView get(@PathVariable long id) { return moods.get(id); }

    @PostMapping
    @PreAuthorize("@adminResourceAccess.canManage('moods', authentication)")
    ResponseEntity<MoodAdminView> create(@RequestBody CreateMoodRequest request,
                                         @AuthenticationPrincipal Jwt jwt) {
        var created = moods.create(request, CurrentAdmin.id(jwt));
        return ResponseEntity.created(URI.create("/api/v1/admin/moods/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@adminResourceAccess.canManage('moods', authentication)")
    MoodAdminView update(@PathVariable long id, @RequestBody UpdateMoodRequest request,
                         @AuthenticationPrincipal Jwt jwt) {
        return moods.update(id, request, CurrentAdmin.id(jwt));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@adminResourceAccess.canManage('moods', authentication)")
    MoodAdminView status(@PathVariable long id, @RequestBody MoodStatusRequest request,
                         @AuthenticationPrincipal Jwt jwt) {
        return moods.setStatus(id, request.active(), CurrentAdmin.id(jwt));
    }
}
