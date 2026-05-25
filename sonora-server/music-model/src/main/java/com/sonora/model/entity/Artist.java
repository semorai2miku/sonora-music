package com.sonora.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_artist")
public class Artist {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 歌手名 */
    private String name;

    /** 头像 URL */
    private String avatar;

    /** 简介 */
    private String description;

    /** 地区 */
    private String region;

    /** 状态: 1-正常 0-下架 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
