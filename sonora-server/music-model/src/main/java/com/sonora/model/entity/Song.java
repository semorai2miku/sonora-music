package com.sonora.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_song")
public class Song {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 歌曲名 */
    private String name;

    /** 歌手 ID 列表，逗号分隔 */
    private String artistIds;

    /** 专辑 ID */
    private Long albumId;

    /** 音频文件在 MinIO 的 objectKey */
    private String fileKey;

    /** 音频时长 (秒) */
    private Integer duration;

    /** 音频大小 (字节) */
    private Long fileSize;

    /** 音频格式 (mp3, flac, wav) */
    private String format;

    /** 音频比特率 (bps) */
    private Integer bitrate;

    /** 封面 URL */
    private String cover;

    /** 歌词 (LRC 格式文本) */
    private String lyrics;

    /** 播放次数 */
    private Long playCount;

    /** 状态: 1-上架 0-下架 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
