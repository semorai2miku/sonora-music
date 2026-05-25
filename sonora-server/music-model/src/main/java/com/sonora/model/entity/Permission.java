package com.sonora.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_permission")
public class Permission {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 权限编码 (如 permission:btn:add) */
    private String code;

    /** 权限名称 */
    private String name;

    /** 权限类型: menu-菜单 button-按钮 api-接口 */
    private String type;

    /** 父权限 ID (菜单树) */
    private Long parentId;

    /** 前端路由路径 */
    private String path;

    /** 前端组件路径 */
    private String component;

    /** 菜单图标 */
    private String icon;

    /** 排序 */
    private Integer sort;

    /** 是否可见: 1-是 0-否 */
    private Integer visible;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
