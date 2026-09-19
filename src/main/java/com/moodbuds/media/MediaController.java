package com.moodbuds.media;

import java.io.IOException;
import java.util.List;

import com.moodbuds.auth.CurrentAdmin;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class MediaController {
    private final MediaService service;
    public MediaController(MediaService service){this.service=service;}

    @PostMapping(value="/api/v1/admin/media/images",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('catalog.manage') or hasRole('SUPER_ADMIN')")
    ResponseEntity<List<MediaDtos.MediaAsset>> upload(@RequestPart("files") List<MultipartFile> files,
                                                      @RequestParam(value="kind",defaultValue="products") String kind,
                                                      @AuthenticationPrincipal Jwt jwt){
        return ResponseEntity.status(201).body(service.upload(files,CurrentAdmin.id(jwt),kind));
    }

    @GetMapping("/api/v1/admin/media/{id}")
    @PreAuthorize("hasAuthority('catalog.read') or hasRole('SUPER_ADMIN')")
    MediaDtos.MediaAsset get(@PathVariable long id){return service.get(id);}

    @DeleteMapping("/api/v1/admin/media/{id}")
    @PreAuthorize("hasAuthority('catalog.manage') or hasRole('SUPER_ADMIN')")
    ResponseEntity<Void> delete(@PathVariable long id,@AuthenticationPrincipal Jwt jwt){service.delete(id,CurrentAdmin.id(jwt));return ResponseEntity.noContent().build();}

    @GetMapping("/api/v1/media/{id}/content")
    ResponseEntity<InputStreamResource> content(@PathVariable long id) throws IOException {
        var media=service.content(id);
        // Use weak caching (1 hour) with validation - files can be deleted anytime
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(media.contentType()))
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofHours(1)).cachePublic().mustRevalidate())
                .header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=\""+media.originalFilename().replace("\"","")+"\"")
                .body(new InputStreamResource(media.inputStream()));
    }
}
