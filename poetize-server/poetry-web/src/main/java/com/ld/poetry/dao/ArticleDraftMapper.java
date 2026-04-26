package com.ld.poetry.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ld.poetry.entity.ArticleDraft;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ArticleDraftMapper extends BaseMapper<ArticleDraft> {

    @Delete("DELETE FROM article_draft WHERE id = #{draftId}")
    int physicalDeleteById(@Param("draftId") String draftId);
}
