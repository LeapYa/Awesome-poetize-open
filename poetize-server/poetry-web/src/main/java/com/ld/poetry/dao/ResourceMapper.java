package com.ld.poetry.dao;

import com.ld.poetry.entity.Resource;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * <p>
 * 资源信息 Mapper 接口
 * </p>
 *
 * @author sara
 * @since 2022-03-06
 */
@Mapper
public interface ResourceMapper extends BaseMapper<Resource> {

    Page<Resource> selectOrphanResources(Page<Resource> page,
                                         @Param("excludedTypes") List<String> excludedTypes,
                                         @Param("orderColumn") String orderColumn,
                                         @Param("asc") boolean asc);

    @Select("SELECT * FROM resource WHERE id = #{id} FOR UPDATE")
    Resource selectByIdForUpdate(@Param("id") Integer id);

    @Select("SELECT * FROM resource WHERE public_id = #{publicId} LIMIT 1")
    Resource findByPublicId(@Param("publicId") String publicId);

    @Select("SELECT * FROM resource WHERE public_id = #{publicId} LIMIT 1 FOR UPDATE")
    Resource findByPublicIdForUpdate(@Param("publicId") String publicId);

    @Select("SELECT * FROM resource WHERE path_hash = SHA2(#{path}, 256) AND path = #{path} LIMIT 1")
    Resource findByPath(@Param("path") String path);

    @Select("SELECT * FROM resource WHERE path_hash = SHA2(#{path}, 256) AND path = #{path} LIMIT 1 FOR UPDATE")
    Resource findByPathForUpdate(@Param("path") String path);

    int countResourceReferences(@Param("path") String path);

}
