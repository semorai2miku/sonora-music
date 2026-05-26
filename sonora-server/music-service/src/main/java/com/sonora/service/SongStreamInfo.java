package com.sonora.service;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.InputStream;

/** 歌曲流媒体信息，用于 Range 请求响应 */
@Data
@AllArgsConstructor
public class SongStreamInfo {
    private InputStream inputStream;
    private long fileSize;
    private String contentType;
    private String fileName;
}
