package com.ld.poetry.dao;

import com.ld.poetry.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * <p>
 * 用户信息表 Mapper 接口
 * </p>
 *
 * @author sara
 * @since 2021-08-12
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 统计用户名占用数（含逻辑删除记录）
     * uk_username 唯一索引对已删除行同样生效，查重必须绕过 @TableLogic 的 deleted=0 过滤，
     * 否则插入时会撞唯一键报 DuplicateKeyException
     */
    @Select("SELECT COUNT(*) FROM user WHERE username = #{username}")
    Long countUsernameIncludeDeleted(@Param("username") String username);

    /**
     * 统计用户名占用数（含逻辑删除记录，排除指定用户），用于修改用户名时查重
     */
    @Select("SELECT COUNT(*) FROM user WHERE username = #{username} AND id != #{excludeId}")
    Long countUsernameIncludeDeletedExcludeId(@Param("username") String username, @Param("excludeId") Integer excludeId);
}
