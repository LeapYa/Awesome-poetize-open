package com.ld.poetry.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ld.poetry.entity.ResourceContentReplacement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ResourceContentReplacementMapper extends BaseMapper<ResourceContentReplacement> {

    @Select("SELECT * FROM resource_content_replacement WHERE operation_id = #{operationId} FOR UPDATE")
    ResourceContentReplacement selectByOperationIdForUpdate(@Param("operationId") String operationId);
}