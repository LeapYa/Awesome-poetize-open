package com.ld.poetry.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ld.poetry.entity.ResourceAlias;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ResourceAliasMapper extends BaseMapper<ResourceAlias> {

    @Select("SELECT id, resource_id, alias_url, source_type, status, create_time, update_time "
            + "FROM resource_alias "
            + "WHERE alias_hash = SHA2(#{aliasUrl}, 256) "
            + "AND alias_url = #{aliasUrl} AND status = 1 LIMIT 1")
    ResourceAlias findActiveByAliasUrl(@Param("aliasUrl") String aliasUrl);

    @Select("SELECT id, resource_id, alias_url, source_type, status, create_time, update_time "
            + "FROM resource_alias "
            + "WHERE alias_hash = SHA2(#{aliasUrl}, 256) "
            + "AND alias_url = #{aliasUrl} AND status = 1 LIMIT 1 FOR UPDATE")
    ResourceAlias findActiveByAliasUrlForUpdate(@Param("aliasUrl") String aliasUrl);
}