package com.sonora.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_comment")
public class Comment {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 评论者用户 ID */
    private Long userId;

    /** 评论目标类型: song / playlist */
    private String targetType;

    /** 评论目标 ID */
    private Long targetId;

    /** 评论内容 */
    private String content;

    /** 父评论 ID (支持回复) */
    private Long parentId;

    /** 点赞数 */
    private Integer likeCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableLogic
    private Integer deleted;
}
