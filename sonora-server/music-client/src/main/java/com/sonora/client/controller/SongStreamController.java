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
                if (range == null) {
                    response.setStatus(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE.value());
                    response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
                    response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes */" + fileSize);
                    return;
                }
                long start = range[0];
                long end = range[1];
                long contentLength = end - start + 1;

                response.setStatus(HttpStatus.PARTIAL_CONTENT.value());
                response.setContentType(info.getContentType());
                response.setContentLengthLong(contentLength);
                response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
                response.setHeader(HttpHeaders.CONTENT_RANGE,
                        "bytes " + start + "-" + end + "/" + fileSize);

                skipFully(inputStream, start);
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

    /** InputStream.skip 允许少跳，循环直到真正到达 Range 起点。 */
    private void skipFully(InputStream inputStream, long bytesToSkip) throws Exception {
        long remaining = bytesToSkip;
        while (remaining > 0) {
            long skipped = inputStream.skip(remaining);
            if (skipped > 0) {
                remaining -= skipped;
                continue;
            }
            if (inputStream.read() == -1) {
                throw new IllegalStateException("音频流在 Range 起点之前结束");
            }
            remaining--;
        }
    }
}
