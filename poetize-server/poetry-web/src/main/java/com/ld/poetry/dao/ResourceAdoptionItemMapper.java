package com.ld.poetry.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ld.poetry.entity.ResourceAdoptionItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ResourceAdoptionItemMapper extends BaseMapper<ResourceAdoptionItem> {

    @Select("SELECT * FROM resource_adoption_item WHERE id = #{id} FOR UPDATE")
    ResourceAdoptionItem selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM resource_adoption_item "
            + "WHERE task_id = #{taskId} AND status = 'PENDING' "
            + "ORDER BY id ASC LIMIT 1 FOR UPDATE")
    ResourceAdoptionItem findNextPendingForUpdate(@Param("taskId") String taskId);
}