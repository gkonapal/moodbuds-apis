package com.moodbuds.admin;

import java.util.Map;

import com.moodbuds.auth.CurrentAdmin;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {
    private final AdminUserService service;
    public AdminUserController(AdminUserService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAuthority('admins.read') or hasRole('SUPER_ADMIN')")
    Object list(@RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) { return service.list(page,size); }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('admins.read') or hasRole('SUPER_ADMIN')")
    Object get(@PathVariable long id) { return service.get(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('admins.manage') or hasRole('SUPER_ADMIN')")
    Object create(@Valid @RequestBody CreateAdminRequest request, @AuthenticationPrincipal Jwt jwt) { return service.create(request, CurrentAdmin.id(jwt)); }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('admins.manage') or hasRole('SUPER_ADMIN')")
    Object update(@PathVariable long id, @Valid @RequestBody UpdateAdminRequest request, @AuthenticationPrincipal Jwt jwt) { return service.update(id,request,CurrentAdmin.id(jwt)); }

    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('admins.manage') or hasRole('SUPER_ADMIN')")
    void password(@PathVariable long id, @RequestBody Map<String,String> body, @AuthenticationPrincipal Jwt jwt) { service.changePassword(id,body.get("password"),CurrentAdmin.id(jwt)); }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('admins.manage') or hasRole('SUPER_ADMIN')")
    void deactivate(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) { service.deactivate(id,CurrentAdmin.id(jwt)); }
}
