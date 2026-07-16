package com.ld.poetry.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ld.poetry.entity.ResourceAdoptionTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ResourceAdoptionTaskMapper extends BaseMapper<ResourceAdoptionTask> {

    @Select("SELECT * FROM resource_adoption_task WHERE task_id = #{taskId} LIMIT 1")
    ResourceAdoptionTask findByTaskId(@Param("taskId") String taskId);

    @Select("SELECT * FROM resource_adoption_task WHERE task_id = #{taskId} LIMIT 1 FOR UPDATE")
    ResourceAdoptionTask findByTaskIdForUpdate(@Param("taskId") String taskId);

    @Select("SELECT task_id FROM resource_adoption_task WHERE status IN ('PENDING', 'RUNNING')")
    List<String> findRecoverableTaskIds();
}