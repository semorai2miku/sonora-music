package com.sonora.admin.controller;

import com.sonora.common.result.R;
import com.sonora.file.service.MinioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理端 — 文件上传
 */
@Tag(name = "管理端-文件管理", description = "文件上传接口")
@RestController
@RequestMapping("/api/admin")
public class FileController {

    private final MinioService minioService;

    public FileController(MinioService minioService) {
        this.minioService = minioService;
    }

    @Operation(summary = "上传文件")
    @PostMapping("/upload")
    public R<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "general") String dir) {
        try {
            String objectKey = minioService.upload(file, dir);
            String url = minioService.getPresignedUrl(objectKey);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("objectKey", objectKey);
            data.put("url", url);
            data.put("fileName", file.getOriginalFilename());
            data.put("fileSize", file.getSize());
            return R.ok(data);
        } catch (Exception e) {
            return R.fail("文件上传失败: " + e.getMessage());
        }
    }
}
