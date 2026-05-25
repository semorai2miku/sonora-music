package com.sonora.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sonora.model.entity.Album;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AlbumMapper extends BaseMapper<Album> {
}
