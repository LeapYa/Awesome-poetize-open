package com.ld.poetry.dao;

import com.ld.poetry.entity.Resource;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

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

}
