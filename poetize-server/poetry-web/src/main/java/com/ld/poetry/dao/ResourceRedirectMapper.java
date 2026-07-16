package com.ld.poetry.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ld.poetry.entity.ResourceRedirect;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ResourceRedirectMapper extends BaseMapper<ResourceRedirect> {

    @Select("SELECT id, resource_id, source_path, target_url, status, create_time, update_time "
            + "FROM resource_redirect "
            + "WHERE source_path_hash = SHA2(#{sourcePath}, 256) "
            + "AND source_path = #{sourcePath} AND status = 1 LIMIT 1")
    ResourceRedirect findActiveBySourcePath(@Param("sourcePath") String sourcePath);
}