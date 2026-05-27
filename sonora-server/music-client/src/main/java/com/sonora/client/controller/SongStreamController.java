package com.sonora.client.controller;

import com.sonora.service.SongService;
import com.sonora.service.SongStreamInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * 客户端 — 音频流媒体播放
 * <p>
 * 支持 HTTP Range 请求，允许 HTML5 Audio 拖拽进度条。
 */
@Tag(name = "客户端-歌曲播放", description = "音频流媒体接口")
@RestController
@RequestMapping("/api/client/songs")
public class SongStreamController {

    private static final Logger log = LoggerFactory.getLogger(SongStreamController.class);

    private final SongService songService;

    public SongStreamController(SongService songService) {
        this.songService = songService;
    }

    @Operation(summary = "流式播放歌曲 (支持 Range)")
    @GetMapping("/{id}/stream")
    public void stream(@PathVariable Long id,
                       HttpServletRequest request,
                       HttpServletResponse response) {

        SongStreamInfo info = songService.getStreamInfo(id);

        long fileSize = info.getFileSize();
        String rangeHeader = request.getHeader("Range");

        try (InputStream inputStream = info.getInputStream();
             OutputStream outputStream = response.getOutputStream()) {

            if (rangeHeader == null || !rangeHeader.startsWith("bytes=")) {
                // 完整返回
                response.setContentType(info.getContentType());
                response.setContentLengthLong(fileSize);
                response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
                response.setHeader("Content-Disposition", "inline; filename=\"" + info.getFileName() + "\"");

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            } else {
                // Range 请求 (拖动进度条)
                long[] range = parseRange(rangeHeader, fileSize);
                long start = range[0];
                long end = range[1];
                long contentLength = end - start + 1;

                response.setStatus(HttpStatus.PARTIAL_CONTENT.value());
                response.setContentType(info.getContentType());
                response.setContentLengthLong(contentLength);
                response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
                response.setHeader(HttpHeaders.CONTENT_RANGE,
                        "bytes " + start + "-" + end + "/" + fileSize);

                inputStream.skip(start);
                byte[] buffer = new byte[8192];
                long remaining = contentLength;
                int bytesRead;
                while (remaining > 0 && (bytesRead = inputStream.read(buffer, 0,
                        (int) Math.min(buffer.length, remaining))) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    remaining -= bytesRead;
                }
            }
            outputStream.flush();

        } catch (Exception e) {
            log.error("流媒体播放失败: songId={}", id, e);
            if (!response.isCommitted()) {
                response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
            }
        }
    }

    /** 解析 Range 请求头: "bytes=0-1023" → [0, 1023] */
    private long[] parseRange(String rangeHeader, long fileSize) {
        try {
            String rangeValue = rangeHeader.substring("bytes=".length());
            String[] parts = rangeValue.split("-");
            long start = Long.parseLong(parts[0]);
            long end = parts.length > 1 && !parts[1].isEmpty()
                    ? Long.parseLong(parts[1])
                    : fileSize - 1;
            if (end >= fileSize) end = fileSize - 1;
            return new long[]{start, end};
        } catch (Exception e) {
            return new long[]{0, fileSize - 1};
        }
    }
}
