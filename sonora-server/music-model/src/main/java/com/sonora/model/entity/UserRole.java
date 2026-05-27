package com.sonora.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_user_role")
public class UserRole {

    /** 用户 ID */
    @TableId("user_id")
    private Long userId;

    /** 角色 ID */
    private Long roleId;
}
