package com.ld.poetry.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ld.poetry.entity.ArticleTranslation;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 文章翻译内容表 Mapper 接口
 * </p>
 *
 * @author leapya
 * @since 2024-05-10
 */
@Mapper
public interface ArticleTranslationMapper extends BaseMapper<ArticleTranslation> {
} 