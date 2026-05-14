package com.ld.poetry.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.entity.Comment;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ld.poetry.vo.BaseRequestVO;
import com.ld.poetry.vo.CommentVO;


/**
 * <p>
 * 文章评论表 服务类
 * </p>
 *
 * @author sara
 * @since 2021-08-13
 */
public interface CommentService extends IService<Comment> {

    PoetryResult saveComment(CommentVO commentVO);

    PoetryResult<Comment> saveAiReplyComment(CommentVO commentVO);

    PoetryResult deleteComment(Integer id);

    PoetryResult deleteCommentById(Integer id);

    PoetryResult<BaseRequestVO> listComment(BaseRequestVO baseRequestVO);

    PoetryResult<Page> listAdminComment(BaseRequestVO baseRequestVO, Boolean isBoss);

    /**
     * 🔧 新接口：子评论懒加载查询
     * 支持分页加载某个评论的子评论
     *
     * @param parentCommentId 父评论ID
     * @param baseRequestVO 基础请求参数（包含source、type等）
     * @param current 当前页码
     * @param size 每页大小（默认10）
     * @return 分页的子评论列表
     */
    PoetryResult<Page<CommentVO>> listChildComments(Integer parentCommentId, BaseRequestVO baseRequestVO, Integer current, Integer size);

    PoetryResult<Integer> likeComment(Integer id, Boolean isLike);
}
