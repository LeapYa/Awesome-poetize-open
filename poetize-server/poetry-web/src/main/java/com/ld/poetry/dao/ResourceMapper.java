package com.ld.poetry.dao;

import com.ld.poetry.entity.Resource;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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

}
