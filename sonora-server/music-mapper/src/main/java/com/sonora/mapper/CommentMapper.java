package com.sonora.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sonora.model.entity.Comment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CommentMapper extends BaseMapper<Comment> {
}
