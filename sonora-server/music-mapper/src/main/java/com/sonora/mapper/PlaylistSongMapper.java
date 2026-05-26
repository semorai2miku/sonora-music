package com.sonora.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sonora.model.entity.PlaylistSong;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PlaylistSongMapper extends BaseMapper<PlaylistSong> {
}
