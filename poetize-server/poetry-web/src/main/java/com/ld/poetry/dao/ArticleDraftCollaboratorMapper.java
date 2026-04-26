package com.ld.poetry.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ld.poetry.entity.ArticleDraftCollaborator;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ArticleDraftCollaboratorMapper extends BaseMapper<ArticleDraftCollaborator> {

    @Delete("DELETE FROM article_draft_collaborator WHERE draft_id = #{draftId}")
    int physicalDeleteByDraftId(@Param("draftId") String draftId);

    @Delete("DELETE FROM article_draft_collaborator WHERE draft_id = #{draftId} AND user_id = #{userId}")
    int physicalDeleteByDraftIdAndUserId(@Param("draftId") String draftId, @Param("userId") Integer userId);
}
