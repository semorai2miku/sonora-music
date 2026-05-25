package com.sonora.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_album")
public class Album {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 专辑名 */
    private String name;

    /** 封面 URL */
    private String cover;

    /** 歌手 ID */
    private Long artistId;

    /** 发行日期 */
    private LocalDate releaseDate;

    /** 专辑描述 */
    private String description;

    /** 专辑类型: album-专辑 single-单曲 ep-EP */
    private String type;

    /** 状态: 1-正常 0-下架 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
