package com.sonora.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sonora.model.entity.Artist;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ArtistMapper extends BaseMapper<Artist> {

    @Delete("DELETE FROM t_artist WHERE id = #{id}")
    int hardDeleteById(@Param("id") Long id);
}
