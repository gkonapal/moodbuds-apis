package com.moodbuds.admin;

import java.net.URI;
import java.util.Map;

import com.moodbuds.auth.CurrentAdmin;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminCrudController {
    private final AdminCrudService service;
    public AdminCrudController(AdminCrudService service){this.service=service;}

    @GetMapping("/{resource:categories|subcategories|gst-rates|products}")
    @PreAuthorize("@adminResourceAccess.canRead(#resource, authentication)")
    Object list(@PathVariable String resource,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(required=false,name="q") String query){return service.list(resource,page,size,query);}

    @GetMapping("/{resource:categories|subcategories|gst-rates|products}/{id}")
    @PreAuthorize("@adminResourceAccess.canRead(#resource, authentication)")
    Object get(@PathVariable String resource,@PathVariable long id){return service.get(resource,id);}

    @PostMapping("/{resource:categories|subcategories|gst-rates|products}")
    @PreAuthorize("@adminResourceAccess.canManage(#resource, authentication)")
    ResponseEntity<?> create(@PathVariable String resource,@RequestBody Map<String,Object> body,@AuthenticationPrincipal Jwt jwt){
        var created=service.create(resource,body,CurrentAdmin.id(jwt));
        return ResponseEntity.created(URI.create("/api/v1/admin/"+resource+"/"+created.get("id"))).body(created);
    }

    @PatchMapping("/{resource:categories|subcategories|gst-rates|products}/{id}")
    @PreAuthorize("@adminResourceAccess.canManage(#resource, authentication)")
    Object update(@PathVariable String resource,@PathVariable long id,@RequestBody Map<String,Object> body,@AuthenticationPrincipal Jwt jwt){return service.update(resource,id,body,CurrentAdmin.id(jwt));}

    @DeleteMapping("/{resource:categories|subcategories|gst-rates|products}/{id}")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    @PreAuthorize("@adminResourceAccess.canManage(#resource, authentication)")
    void delete(@PathVariable String resource,@PathVariable long id,@AuthenticationPrincipal Jwt jwt){service.delete(resource,id,CurrentAdmin.id(jwt));}
}
