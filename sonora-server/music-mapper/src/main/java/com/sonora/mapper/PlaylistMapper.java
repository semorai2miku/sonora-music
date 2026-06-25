package com.sonora.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sonora.model.entity.Playlist;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PlaylistMapper extends BaseMapper<Playlist> {

    @Update("""
            UPDATE t_playlist
            SET play_count = COALESCE(play_count, 0) + #{delta}
            WHERE id = #{playlistId} AND deleted = 0
            """)
    int increasePlayCount(@Param("playlistId") Long playlistId, @Param("delta") long delta);

    @Update("""
            UPDATE t_playlist
            SET collect_count = (
                SELECT COUNT(*)
                FROM t_user_favorite
                WHERE target_type = 'playlist' AND target_id = #{playlistId}
            )
            WHERE id = #{playlistId} AND deleted = 0
            """)
    int refreshCollectCount(@Param("playlistId") Long playlistId);
}
