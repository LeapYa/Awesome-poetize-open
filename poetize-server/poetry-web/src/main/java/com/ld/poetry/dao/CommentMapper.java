package com.ld.poetry.dao;

import com.ld.poetry.entity.Comment;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 文章评论表 Mapper 接口
 * </p>
 *
 * @author sara
 * @since 2021-08-13
 */
@Mapper
public interface CommentMapper extends BaseMapper<Comment> {

}
