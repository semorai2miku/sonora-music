package com.sonora.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_role")
public class Role {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色编码 (ROLE_ADMIN, ROLE_EDITOR, etc.) */
    private String code;

    /** 角色名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 排序 */
    private Integer sort;

    /** 状态: 1-正常 0-禁用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
