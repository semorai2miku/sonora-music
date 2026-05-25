package com.sonora.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_playlist_song")
public class PlaylistSong {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 歌单 ID */
    private Long playlistId;

    /** 歌曲 ID */
    private Long songId;

    /** 排序 */
    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
