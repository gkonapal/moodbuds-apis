package com.moodbuds.admin;

import static com.moodbuds.admin.AdminCouponDtos.*;

import java.net.URI;
import java.util.Map;

import com.moodbuds.auth.CurrentAdmin;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/coupons")
@PreAuthorize("hasAuthority('coupons.manage') or hasRole('SUPER_ADMIN')")
public class AdminCouponController {
    private final AdminCouponService service;
    public AdminCouponController(AdminCouponService service){this.service=service;}

    @GetMapping Object list(@RequestParam(required=false,name="q")String q,@RequestParam(required=false)String status,
                            @RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return service.list(q,status,page,size);}
    @GetMapping("/stats") Object stats(){return service.stats();}
    @GetMapping("/{id}") Object get(@PathVariable long id){return service.get(id);}
    @PostMapping ResponseEntity<?> create(@Valid @RequestBody CouponRequest body,@AuthenticationPrincipal Jwt jwt){
        var result=service.create(body,CurrentAdmin.id(jwt));return ResponseEntity.created(URI.create("/api/v1/admin/coupons/"+result.id())).body(result);}
    @PutMapping("/{id}") Object update(@PathVariable long id,@Valid @RequestBody CouponRequest body,@AuthenticationPrincipal Jwt jwt){return service.update(id,body,CurrentAdmin.id(jwt));}
    @PatchMapping("/{id}/status") Object status(@PathVariable long id,@RequestBody Map<String,Boolean> body,@AuthenticationPrincipal Jwt jwt){
        if(!body.containsKey("active"))throw com.moodbuds.common.ApiException.badRequest("ACTIVE_REQUIRED","active is required");
        return service.status(id,body.get("active"),CurrentAdmin.id(jwt));}
    @GetMapping("/{id}/assignments") Object assignments(@PathVariable long id,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return service.assignments(id,page,size);}
    @PostMapping("/{id}/assignments") Object assign(@PathVariable long id,@Valid @RequestBody AssignmentRequest body,@AuthenticationPrincipal Jwt jwt){return service.assign(id,body,CurrentAdmin.id(jwt));}
    @DeleteMapping("/{id}/assignments/{userId}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    void revoke(@PathVariable long id,@PathVariable long userId,@AuthenticationPrincipal Jwt jwt){service.revoke(id,userId,CurrentAdmin.id(jwt));}
    @GetMapping("/{id}/usage") Object usage(@PathVariable long id,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return service.usage(id,page,size);}
}
