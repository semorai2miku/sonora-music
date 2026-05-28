package com.sonora.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sonora.common.enums.ResultCode;
import com.sonora.common.exception.BusinessException;
import com.sonora.file.service.MinioService;
import com.sonora.mapper.SongMapper;
import com.sonora.model.entity.Song;
import com.sonora.service.SongService;
import com.sonora.service.SongStreamInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class SongServiceImpl extends ServiceImpl<SongMapper, Song> implements SongService {

    private final MinioService minioService;

    @Override
    public Page<Song> pageSongs(int pageNum, int pageSize, String keyword, Long albumId, Long artistId) {
        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Song::getName, keyword);
        }
        if (albumId != null) {
            wrapper.eq(Song::getAlbumId, albumId);
        }
        if (artistId != null) {
            wrapper.apply("FIND_IN_SET({0}, artist_ids)", artistId);
        }
        wrapper.orderByAsc(Song::getId);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public Song createSong(MultipartFile audioFile, MultipartFile coverFile, Song song) {
        // 1. 上传音频文件到 MinIO
        try {
            String audioKey = minioService.upload(audioFile, "audio");
            song.setFileKey(audioKey);
            song.setFileSize(audioFile.getSize());

            // 提取格式 (不含点号)
            String originalName = audioFile.getOriginalFilename();
            if (originalName != null && originalName.contains(".")) {
                song.setFormat(originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase());
            }
        } catch (Exception e) {
            log.error("音频文件上传失败", e);
            throw new BusinessException(ResultCode.FILE_UPLOAD_FAILED);
        }

        // 2. 上传封面 (如果有)
        if (coverFile != null && !coverFile.isEmpty()) {
            try {
                String coverKey = minioService.upload(coverFile, "cover");
                song.setCover(minioService.getPresignedUrl(coverKey));
            } catch (Exception e) {
                log.error("封面上传失败", e);
                // 封面上传失败不影响主流程，继续
            }
        }

        // 3. 保存歌曲记录
        save(song);
        return song;
    }

    @Override
    public Song updateSong(Long id, Song song) {
        Song existing = getById(id);
        if (existing == null) {
            throw new BusinessException(ResultCode.SONG_NOT_FOUND);
        }
        song.setId(id);
        song.setFileKey(existing.getFileKey());     // 不允许通过此接口更换文件
        song.setFileSize(existing.getFileSize());
        song.setFormat(existing.getFormat());
        updateById(song);
        return getById(id);
    }

    @Override
    public void deleteSong(Long id) {
        Song song = getById(id);
        if (song == null) {
            throw new BusinessException(ResultCode.SONG_NOT_FOUND);
        }

        // 删除 MinIO 文件
        if (StringUtils.hasText(song.getFileKey())) {
            try {
                minioService.delete(song.getFileKey());
            } catch (Exception e) {
                log.warn("删除 MinIO 文件失败: {}", song.getFileKey(), e);
            }
        }

        // 逻辑删除
        removeById(id);
    }

    @Override
    public SongStreamInfo getStreamInfo(Long songId) {
        Song song = getById(songId);
        if (song == null || !StringUtils.hasText(song.getFileKey())) {
            throw new BusinessException(ResultCode.SONG_NOT_FOUND);
        }

        try {
            InputStream inputStream = minioService.getObject(song.getFileKey());
            String contentType = "audio/" + (song.getFormat() != null ? song.getFormat() : "mpeg");
            String fileName = song.getName() + "." + song.getFormat();
            return new SongStreamInfo(inputStream, song.getFileSize(), contentType, fileName);
        } catch (Exception e) {
            log.error("获取歌曲流失败: songId={}", songId, e);
            throw new BusinessException(ResultCode.FILE_NOT_FOUND);
        }
    }
}
