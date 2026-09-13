package com.moodbuds.admin;

import java.net.URI;

import com.moodbuds.auth.CurrentAdmin;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/products")
public class ConsolidatedProductController {
    private final ConsolidatedProductService service;
    public ConsolidatedProductController(ConsolidatedProductService service){this.service=service;}

    @PostMapping("/complete")
    @PreAuthorize("hasAuthority('catalog.manage') or hasRole('SUPER_ADMIN')")
    ResponseEntity<ProductAdminDtos.CompleteProductResponse> create(@Valid @RequestBody ProductAdminDtos.CompleteProductRequest request,@AuthenticationPrincipal Jwt jwt){
        var created=service.create(request,CurrentAdmin.id(jwt));
        return ResponseEntity.created(URI.create("/api/v1/admin/products/"+created.product().id()+"/full")).body(created);
    }
    @PutMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('catalog.manage') or hasRole('SUPER_ADMIN')")
    ProductAdminDtos.CompleteProductResponse update(@PathVariable long id,@Valid @RequestBody ProductAdminDtos.CompleteProductRequest request,@AuthenticationPrincipal Jwt jwt){return service.update(id,request,CurrentAdmin.id(jwt));}
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('catalog.manage') or hasRole('SUPER_ADMIN')")
    ProductAdminDtos.CompleteProductResponse publish(@PathVariable long id,@AuthenticationPrincipal Jwt jwt){return service.publish(id,CurrentAdmin.id(jwt));}
    @PostMapping("/{id}/unpublish")
    @PreAuthorize("hasAuthority('catalog.manage') or hasRole('SUPER_ADMIN')")
    ProductAdminDtos.CompleteProductResponse unpublish(@PathVariable long id,@AuthenticationPrincipal Jwt jwt){return service.unpublish(id,CurrentAdmin.id(jwt));}
    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('catalog.manage') or hasRole('SUPER_ADMIN')")
    ProductAdminDtos.CompleteProductResponse archive(@PathVariable long id,@AuthenticationPrincipal Jwt jwt){return service.archive(id,CurrentAdmin.id(jwt));}
}
