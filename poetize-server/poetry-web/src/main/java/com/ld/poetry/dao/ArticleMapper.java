package com.ld.poetry.dao;

import com.ld.poetry.entity.Article;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * <p>
 * 文章表 Mapper 接口
 * </p>
 *
 * @author sara
 * @since 2021-08-13
 */
@Mapper
public interface ArticleMapper extends BaseMapper<Article> {

    @Update("update article set view_count=view_count+1 where id=#{id}")
    int updateViewCount(@Param("id") Integer id);

    @Update("update article set view_count = case when view_count >= #{count} then view_count - #{count} else 0 end where id=#{id}")
    int decrementViewCount(@Param("id") Integer id, @Param("count") Integer count);

    @Select("""
            SELECT id FROM article
            WHERE deleted = 0 AND article_title REGEXP #{regexPattern}
            LIMIT 100
            """)
    List<Integer> selectIdsByTitleRegex(@Param("regexPattern") String regexPattern);

    @Select("""
            SELECT id FROM article
            WHERE deleted = 0 AND article_content REGEXP #{regexPattern}
            LIMIT 100
            """)
    List<Integer> selectIdsByContentRegex(@Param("regexPattern") String regexPattern);
}
