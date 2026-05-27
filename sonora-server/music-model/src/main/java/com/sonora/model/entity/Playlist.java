package com.sonora.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_playlist")
public class Playlist {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 歌单名称 */
    private String name;

    /** 封面 URL */
    private String cover;

    /** 创建者用户 ID */
    private Long userId;

    /** 歌单描述 */
    private String description;

    /** 歌单类型: liked-喜欢的音乐 normal-自建歌单 */
    private String type;

    /** 是否置顶: 1-置顶 0-普通 */
    private Integer pinned;

    /** 分类标签，逗号分隔 */
    private String tags;

    /** 播放次数 */
    private Long playCount;

    /** 收藏次数 */
    private Long collectCount;

    /** 状态: 1-公开 0-私密 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
