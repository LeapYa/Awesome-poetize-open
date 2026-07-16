package com.ld.poetry.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ld.poetry.entity.ResourceLocation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ResourceLocationMapper extends BaseMapper<ResourceLocation> {

    @Select("SELECT * FROM resource_location WHERE resource_id = #{resourceId} ORDER BY id ASC")
    List<ResourceLocation> findByResourceId(@Param("resourceId") Integer resourceId);

    @Select("SELECT * FROM resource_location WHERE resource_id = #{resourceId} ORDER BY id ASC FOR UPDATE")
    List<ResourceLocation> findByResourceIdForUpdate(@Param("resourceId") Integer resourceId);

    @Select("SELECT * FROM resource_location WHERE id = #{id} FOR UPDATE")
    ResourceLocation selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM resource_location "
            + "WHERE store_type = #{storeType} "
            + "AND access_path_hash = SHA2(#{accessPath}, 256) "
            + "AND access_path = #{accessPath} LIMIT 1 FOR UPDATE")
    ResourceLocation findByStoreAndAccessPathForUpdate(@Param("storeType") String storeType,
                                                       @Param("accessPath") String accessPath);
}