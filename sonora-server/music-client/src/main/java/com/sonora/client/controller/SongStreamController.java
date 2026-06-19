package com.sonora.client.controller;

import com.sonora.file.service.MinioService;
import com.sonora.service.SongService;
import com.sonora.service.SongStreamInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.connector.ClientAbortException;
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
    private final MinioService minioService;

    public SongStreamController(SongService songService, MinioService minioService) {
        this.songService = songService;
        this.minioService = minioService;
    }

    @Operation(summary = "流式播放歌曲 (支持 Range)")
    @GetMapping("/{id}/stream")
    public void stream(@PathVariable Long id,
                       HttpServletRequest request,
                       HttpServletResponse response) {

        SongStreamInfo info = songService.getStreamInfo(id);

        long fileSize = info.getFileSize();
        String rangeHeader = request.getHeader("Range");
        long start = 0;
        long end = fileSize - 1;
        boolean partial = rangeHeader != null && rangeHeader.startsWith("bytes=");

        if (partial) {
            long[] range = parseRange(rangeHeader, fileSize);
            if (range == null) {
                response.setStatus(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE.value());
                response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
                response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes */" + fileSize);
                return;
            }
            start = range[0];
            end = range[1];
        }

        long contentLength = end - start + 1;
        response.setStatus(partial ? HttpStatus.PARTIAL_CONTENT.value() : HttpStatus.OK.value());
        response.setContentType(info.getContentType());
        response.setContentLengthLong(contentLength);
        response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
        response.setHeader("Content-Disposition", "inline; filename=\"" + info.getFileName() + "\"");
        if (partial) {
            response.setHeader(HttpHeaders.CONTENT_RANGE,
                    "bytes " + start + "-" + end + "/" + fileSize);
        }

        try (InputStream inputStream = minioService.getObject(info.getObjectKey(), start, contentLength);
             OutputStream outputStream = response.getOutputStream()) {
            byte[] buffer = new byte[64 * 1024];
            long remaining = contentLength;
            int bytesRead;
            while (remaining > 0 && (bytesRead = inputStream.read(
                    buffer, 0, (int) Math.min(buffer.length, remaining))) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                remaining -= bytesRead;
            }
            outputStream.flush();
        } catch (ClientAbortException e) {
            log.debug("客户端取消音频流: songId={}, range={}-{}", id, start, end);
        } catch (Exception e) {
            log.error("流媒体播放失败: songId={}, range={}-{}", id, start, end, e);
            if (!response.isCommitted()) {
                response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
            }
        }
    }

    /** 解析单段 Range，兼容闭区间、开放区间和后缀区间。 */
    private long[] parseRange(String rangeHeader, long fileSize) {
        try {
            if (fileSize <= 0) return null;
            String rangeValue = rangeHeader.substring("bytes=".length()).trim();
            if (rangeValue.isEmpty() || rangeValue.contains(",")) return null;

            String[] parts = rangeValue.split("-", -1);
            if (parts.length != 2) return null;

            long start;
            long end;
            if (parts[0].isEmpty()) {
                long suffixLength = Long.parseLong(parts[1]);
                if (suffixLength <= 0) return null;
                start = Math.max(0, fileSize - suffixLength);
                end = fileSize - 1;
            } else {
                start = Long.parseLong(parts[0]);
                if (start < 0 || start >= fileSize) return null;
                end = parts[1].isEmpty() ? fileSize - 1 : Long.parseLong(parts[1]);
                if (end < start) return null;
                end = Math.min(end, fileSize - 1);
            }
            return new long[]{start, end};
        } catch (Exception e) {
            return null;
        }
    }

}
