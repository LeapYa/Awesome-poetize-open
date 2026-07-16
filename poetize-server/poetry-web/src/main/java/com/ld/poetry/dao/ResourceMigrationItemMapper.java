package com.ld.poetry.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ld.poetry.entity.ResourceMigrationItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface ResourceMigrationItemMapper extends BaseMapper<ResourceMigrationItem> {

    @Update("UPDATE resource_migration_item SET status = 'SWITCHED', error_message = NULL "
            + "WHERE id = #{id} AND status = 'VERIFIED' "
            + "AND source_location_id = #{sourceLocationId} AND target_location_id = #{targetLocationId}")
    int markSwitched(@Param("id") Long id,
                     @Param("sourceLocationId") Long sourceLocationId,
                     @Param("targetLocationId") Long targetLocationId);

    @Select("SELECT status, COUNT(*) AS item_count FROM resource_migration_item "
            + "WHERE task_id = #{taskId} GROUP BY status")
    List<Map<String, Object>> countByStatus(@Param("taskId") String taskId);
}