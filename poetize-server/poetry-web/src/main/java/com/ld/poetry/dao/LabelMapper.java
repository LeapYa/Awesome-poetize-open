package com.ld.poetry.dao;

import com.ld.poetry.entity.Label;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 标签 Mapper 接口
 * </p>
 *
 * @author sara
 * @since 2021-09-14
 */
@Mapper
public interface LabelMapper extends BaseMapper<Label> {

}
