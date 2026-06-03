package com.sonora.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sonora.common.constant.Constants;
import com.sonora.common.enums.ResultCode;
import com.sonora.common.exception.BusinessException;
import com.sonora.file.service.MinioService;
import com.sonora.mapper.AlbumMapper;
import com.sonora.mapper.SongMapper;
import com.sonora.model.entity.Album;
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
    private final AlbumMapper albumMapper;

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
            applyAudioFile(song, audioFile);
        } catch (Exception e) {
            log.error("音频文件上传失败", e);
            throw new BusinessException(ResultCode.FILE_UPLOAD_FAILED);
        }

        // 2. 歌曲封面始终跟随所属专辑，没有专辑则使用默认封面
        song.setCover(resolveManagedSongCover(song.getAlbumId()));

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

        Song updated = new Song();
        updated.setId(id);
        updated.setName(StringUtils.hasText(song.getName()) ? song.getName() : existing.getName());
        updated.setArtistIds(StringUtils.hasText(song.getArtistIds()) ? song.getArtistIds() : existing.getArtistIds());
        updated.setAlbumId(song.getAlbumId() != null ? song.getAlbumId() : existing.getAlbumId());
        updated.setFileKey(existing.getFileKey());
        updated.setFileSize(existing.getFileSize());
        updated.setFormat(existing.getFormat());
        updated.setBitrate(existing.getBitrate());
        updated.setDuration(song.getDuration() != null ? song.getDuration() : existing.getDuration());
        updated.setLyrics(song.getLyrics() != null ? song.getLyrics() : existing.getLyrics());
        updated.setPlayCount(song.getPlayCount() != null ? song.getPlayCount() : existing.getPlayCount());
        updated.setStatus(song.getStatus() != null ? song.getStatus() : existing.getStatus());
        updated.setCover(resolveManagedSongCover(updated.getAlbumId()));
        updateById(updated);
        deleteManagedObject(existing.getCover(), updated.getCover());
        return getById(id);
    }

    @Override
    public Song replaceSong(Long id, MultipartFile audioFile, MultipartFile coverFile, Song song) {
        Song existing = getById(id);
        if (existing == null) {
            throw new BusinessException(ResultCode.SONG_NOT_FOUND);
        }
        if (audioFile == null || audioFile.isEmpty()) {
            throw new BusinessException(ResultCode.FILE_UPLOAD_FAILED.getCode(), "请先选择新的音频文件");
        }

        String previousAudioKey = existing.getFileKey();
        String previousCoverKey = existing.getCover();
        String newAudioKey = null;

        try {
            Song updated = new Song();
            updated.setId(id);
            updated.setName(StringUtils.hasText(song.getName()) ? song.getName() : existing.getName());
            updated.setArtistIds(StringUtils.hasText(song.getArtistIds()) ? song.getArtistIds() : existing.getArtistIds());
            updated.setAlbumId(song.getAlbumId() != null ? song.getAlbumId() : existing.getAlbumId());
            updated.setLyrics(song.getLyrics() != null ? song.getLyrics() : existing.getLyrics());
            updated.setDuration(song.getDuration() != null ? song.getDuration() : existing.getDuration());
            updated.setPlayCount(song.getPlayCount() != null ? song.getPlayCount() : existing.getPlayCount());
            updated.setStatus(song.getStatus() != null ? song.getStatus() : existing.getStatus());
            updated.setBitrate(existing.getBitrate());

            applyAudioFile(updated, audioFile);
            newAudioKey = updated.getFileKey();

            updated.setCover(resolveManagedSongCover(updated.getAlbumId()));

            updateById(updated);

            deleteManagedObject(previousAudioKey, updated.getFileKey());
            deleteManagedObject(previousCoverKey, updated.getCover());
            return getById(id);
        } catch (BusinessException e) {
            cleanupUploadedObject(newAudioKey);
            throw e;
        } catch (Exception e) {
            cleanupUploadedObject(newAudioKey);
            log.error("替换歌曲文件失败: songId={}", id, e);
            throw new BusinessException(ResultCode.FILE_UPLOAD_FAILED.getCode(), "替换歌曲失败");
        }
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

    private void applyAudioFile(Song song, MultipartFile audioFile) throws Exception {
        String audioKey = minioService.upload(audioFile, "audio");
        song.setFileKey(audioKey);
        song.setFileSize(audioFile.getSize());

        String originalName = audioFile.getOriginalFilename();
        if (originalName != null && originalName.contains(".")) {
            song.setFormat(originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase());
            return;
        }
        song.setFormat("mp3");
    }

    private String resolveManagedSongCover(Long albumId) {
        if (albumId == null) {
            return Constants.DEFAULT_COVER;
        }
        Album album = albumMapper.selectById(albumId);
        if (album != null && StringUtils.hasText(album.getCover())) {
            return minioService.normalizeForStorage(album.getCover());
        }
        return Constants.DEFAULT_COVER;
    }

    private void deleteManagedObject(String previousValue, String nextValue) {
        String previousKey = minioService.normalizeForStorage(previousValue);
        String nextKey = minioService.normalizeForStorage(nextValue);
        if (!StringUtils.hasText(previousKey)
                || Constants.DEFAULT_AVATAR.equals(previousKey)
                || Constants.DEFAULT_COVER.equals(previousKey)
                || previousKey.equals(nextKey)) {
            return;
        }
        try {
            minioService.delete(previousKey);
        } catch (Exception e) {
            log.warn("删除旧文件失败: {}", previousKey, e);
        }
    }

    private void cleanupUploadedObject(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            return;
        }
        try {
            minioService.delete(objectKey);
        } catch (Exception e) {
            log.warn("清理已上传文件失败: {}", objectKey, e);
        }
    }
}
