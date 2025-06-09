package com.ld.poetry.im.http.dao;

import com.ld.poetry.im.http.entity.ImChatUserGroupMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 群聊记录 Mapper 接口
 * </p>
 *
 * @author sara
 * @since 2021-12-02
 */
@Mapper
public interface ImChatUserGroupMessageMapper extends BaseMapper<ImChatUserGroupMessage> {

}
