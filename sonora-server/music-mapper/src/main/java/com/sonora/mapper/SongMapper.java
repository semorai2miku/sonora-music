package com.sonora.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sonora.model.entity.Song;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SongMapper extends BaseMapper<Song> {

    @Update("""
            UPDATE t_song
            SET play_count = COALESCE(play_count, 0) + #{delta}
            WHERE id = #{songId} AND deleted = 0
            """)
    int increasePlayCount(@Param("songId") Long songId, @Param("delta") long delta);

    @Update("""
            UPDATE t_song
            SET like_count = (
                SELECT COUNT(*)
                FROM t_user_favorite
                WHERE target_type = 'song' AND target_id = #{songId}
            )
            WHERE id = #{songId} AND deleted = 0
            """)
    int refreshLikeCount(@Param("songId") Long songId);
}
