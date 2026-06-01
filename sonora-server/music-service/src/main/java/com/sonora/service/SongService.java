package com.sonora.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sonora.model.entity.Song;
import org.springframework.web.multipart.MultipartFile;

public interface SongService extends IService<Song> {

    /** 分页查询歌曲 */
    Page<Song> pageSongs(int pageNum, int pageSize, String keyword, Long albumId, Long artistId);

    /** 上传音频文件并创建歌曲记录 */
    Song createSong(MultipartFile audioFile, MultipartFile coverFile, Song song);

    /** 更新歌曲元数据 */
    Song updateSong(Long id, Song song);

    /** 替换歌曲音频文件并更新信息 */
    Song replaceSong(Long id, MultipartFile audioFile, MultipartFile coverFile, Song song);

    /** 删除歌曲 (逻辑删除 + 删除 MinIO 文件) */
    void deleteSong(Long id);

    /** 获取歌曲流媒体信息 (文件大小、content-type、输入流) */
    SongStreamInfo getStreamInfo(Long songId);
}
