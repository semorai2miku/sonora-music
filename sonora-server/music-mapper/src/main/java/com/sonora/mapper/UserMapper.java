package com.sonora.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sonora.model.entity.User;
import org.apache.ibatis.annotations.Delete;
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
            <if test="username != null and username != ''">
              AND u.username LIKE CONCAT('%', #{username}, '%')
            </if>
            <if test="email != null and email != ''">
              AND u.email LIKE CONCAT('%', #{email}, '%')
            </if>
            <if test="phone != null and phone != ''">
              AND u.phone LIKE CONCAT('%', #{phone}, '%')
            </if>
            <if test="status != null">
              AND u.status = #{status}
            </if>
            ORDER BY u.id ASC
            </script>
            """)
    Page<User> selectClientUserPage(Page<User> page,
                                    @Param("username") String username,
                                    @Param("email") String email,
                                    @Param("phone") String phone,
                                    @Param("status") Integer status);

    @Delete("DELETE FROM sys_user WHERE id = #{id}")
    int hardDeleteById(@Param("id") Long id);

    @Delete("""
            DELETE FROM sys_user
            WHERE deleted = 1
              AND (
                username = #{username}
                OR email = #{email}
                OR profile_id = #{profileId}
              )
            """)
    int purgeDeletedIdentity(@Param("username") String username,
                             @Param("email") String email,
                             @Param("profileId") String profileId);
}
