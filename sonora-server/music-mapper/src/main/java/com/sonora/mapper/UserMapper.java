package com.sonora.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sonora.model.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("""
            <script>
            SELECT DISTINCT u.*
            FROM sys_user u
            INNER JOIN sys_user_role ur ON u.id = ur.user_id
            INNER JOIN sys_role r ON ur.role_id = r.id
            WHERE u.deleted = 0
              AND r.code = 'USER'
            <if test="keyword != null and keyword != ''">
              AND (
                u.username LIKE CONCAT('%', #{keyword}, '%')
                OR u.nickname LIKE CONCAT('%', #{keyword}, '%')
                OR u.profile_id LIKE CONCAT('%', #{keyword}, '%')
              )
            </if>
            <if test="status != null">
              AND u.status = #{status}
            </if>
            ORDER BY u.created_at DESC
            </script>
            """)
    Page<User> selectClientUserPage(Page<User> page,
                                    @Param("keyword") String keyword,
                                    @Param("status") Integer status);
}
