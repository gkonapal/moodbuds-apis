package com.moodbuds.admin;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/quiz")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class QuizAdminController {
    private final AdminCrudService service;
    public QuizAdminController(AdminCrudService service){this.service=service;}
    private String key(String resource){return "quiz-"+resource;}

    @GetMapping("/{resource:paths|questions|options}") Object list(@PathVariable String resource,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size){return service.list(key(resource),page,size,null);}
    @GetMapping("/{resource:paths|questions|options}/{id}") Object get(@PathVariable String resource,@PathVariable long id){return service.get(key(resource),id);}
}
