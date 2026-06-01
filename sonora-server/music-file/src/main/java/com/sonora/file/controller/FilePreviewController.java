package com.sonora.file.controller;

import com.sonora.file.service.MinioService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.util.Set;

@RestController
@RequestMapping("/api/files")
public class FilePreviewController {

    private static final Set<String> PUBLIC_IMAGE_PREFIXES = Set.of(
            "avatar/",
            "artist-avatar/",
            "album-cover/",
            "song-cover/",
            "playlist-cover/",
            "banner/",
            "cover/"
    );

    private final MinioService minioService;

    public FilePreviewController(MinioService minioService) {
        this.minioService = minioService;
    }

    @GetMapping("/preview")
    public ResponseEntity<InputStreamResource> preview(@RequestParam("key") String key) {
        String objectKey = minioService.normalizeForStorage(key);
        if (!StringUtils.hasText(objectKey)
                || objectKey.startsWith("/default-")
                || !isAllowedImageKey(objectKey)) {
            return ResponseEntity.notFound().build();
        }
        try {
            String contentType = minioService.getContentType(objectKey);
            if (!StringUtils.hasText(contentType) || !contentType.toLowerCase().startsWith("image/")) {
                return ResponseEntity.notFound().build();
            }
            InputStream inputStream = minioService.getObject(objectKey);
            MediaType mediaType = StringUtils.hasText(contentType)
                    ? MediaType.parseMediaType(contentType)
                    : MediaType.APPLICATION_OCTET_STREAM;
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .cacheControl(CacheControl.noCache())
                    .body(new InputStreamResource(inputStream));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    private boolean isAllowedImageKey(String objectKey) {
        return PUBLIC_IMAGE_PREFIXES.stream().anyMatch(objectKey::startsWith);
    }
}
