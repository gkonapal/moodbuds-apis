package com.moodbuds.admin;

import java.util.Map;

import com.moodbuds.auth.CurrentAdmin;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/quiz")
@PreAuthorize("hasAuthority('quiz.manage') or hasRole('SUPER_ADMIN')")
public class QuizAdminController {
    private final AdminCrudService service;
    public QuizAdminController(AdminCrudService service){this.service=service;}
    private String key(String resource){return "quiz-"+resource;}

    @GetMapping("/{resource:paths|questions|options}") Object list(@PathVariable String resource,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size){return service.list(key(resource),page,size,null);}
    @GetMapping("/{resource:paths|questions|options}/{id}") Object get(@PathVariable String resource,@PathVariable long id){return service.get(key(resource),id);}
    @PostMapping("/{resource:paths|questions|options}") @ResponseStatus(HttpStatus.CREATED) Object create(@PathVariable String resource,@RequestBody Map<String,Object> body,@AuthenticationPrincipal Jwt jwt){return service.create(key(resource),body,CurrentAdmin.id(jwt));}
    @PatchMapping("/{resource:paths|questions|options}/{id}") Object update(@PathVariable String resource,@PathVariable long id,@RequestBody Map<String,Object> body,@AuthenticationPrincipal Jwt jwt){return service.update(key(resource),id,body,CurrentAdmin.id(jwt));}
    @DeleteMapping("/{resource:paths|questions|options}/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void delete(@PathVariable String resource,@PathVariable long id,@AuthenticationPrincipal Jwt jwt){service.delete(key(resource),id,CurrentAdmin.id(jwt));}
}
