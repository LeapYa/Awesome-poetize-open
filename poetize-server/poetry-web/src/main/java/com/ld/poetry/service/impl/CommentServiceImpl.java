package com.ld.poetry.service.impl;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.dao.ArticleMapper;
import com.ld.poetry.dao.CommentMapper;
import com.ld.poetry.entity.Article;
import com.ld.poetry.entity.Comment;
import com.ld.poetry.entity.User;
import com.ld.poetry.enums.CodeMsg;
import com.ld.poetry.enums.CommentTypeEnum;
import com.ld.poetry.service.CommentService;
import com.ld.poetry.utils.*;
import com.ld.poetry.utils.mail.MailSendUtil;
import com.ld.poetry.vo.BaseRequestVO;
import com.ld.poetry.vo.CommentVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 文章评论表 服务实现类
 * </p>
 *
 * @author sara
 * @since 2021-08-13
 */
@SuppressWarnings("unchecked")
@Service
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements CommentService {

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private CommonQuery commonQuery;

    @Autowired
    private MailSendUtil mailSendUtil;

    @Override
    public PoetryResult saveComment(CommentVO commentVO) {
        if (CommentTypeEnum.getEnumByCode(commentVO.getType()) == null) {
            return PoetryResult.fail("评论来源类型不存在！");
        }
        Article one = null;
        if (CommentTypeEnum.COMMENT_TYPE_ARTICLE.getCode().equals(commentVO.getType())) {
            LambdaQueryChainWrapper<Article> articleWrapper = new LambdaQueryChainWrapper<>(articleMapper);
            one = articleWrapper.eq(Article::getId, commentVO.getSource()).select(Article::getUserId, Article::getArticleTitle, Article::getCommentStatus).one();

            if (one == null) {
                return PoetryResult.fail("文章不存在");
            } else {
                if (!one.getCommentStatus()) {
                    return PoetryResult.fail("评论功能已关闭！");
                }
            }
        }


        Comment comment = new Comment();
        comment.setSource(commentVO.getSource());
        comment.setType(commentVO.getType());
        comment.setCommentContent(commentVO.getCommentContent());
        comment.setParentCommentId(commentVO.getParentCommentId());
        comment.setFloorCommentId(commentVO.getFloorCommentId());
        comment.setParentUserId(commentVO.getParentUserId());
        comment.setUserId(PoetryUtil.getUserId());
        if (StringUtils.hasText(commentVO.getCommentInfo())) {
            comment.setCommentInfo(commentVO.getCommentInfo());
        }
        save(comment);

        try {
            mailSendUtil.sendCommentMail(commentVO, one, this);
        } catch (Exception e) {
            log.error("发送评论邮件失败：", e);
        }

        return PoetryResult.success();
    }

    @Override
    public PoetryResult deleteComment(Integer id) {
        Integer userId = PoetryUtil.getUserId();
        lambdaUpdate().eq(Comment::getId, id)
                .eq(Comment::getUserId, userId)
                .remove();
        return PoetryResult.success();
    }

    @Override
    public PoetryResult<BaseRequestVO> listComment(BaseRequestVO baseRequestVO) {
        if (baseRequestVO.getSource() == null || !StringUtils.hasText(baseRequestVO.getCommentType())) {
            return PoetryResult.fail(CodeMsg.PARAMETER_ERROR);
        }

        if (CommentTypeEnum.COMMENT_TYPE_ARTICLE.getCode().equals(baseRequestVO.getCommentType())) {
            LambdaQueryChainWrapper<Article> articleWrapper = new LambdaQueryChainWrapper<>(articleMapper);
            Article one = articleWrapper.eq(Article::getId, baseRequestVO.getSource()).select(Article::getCommentStatus).one();

            if (one != null && !one.getCommentStatus()) {
                return PoetryResult.fail("评论功能已关闭！");
            }
        }

        if (baseRequestVO.getFloorCommentId() == null) {
            Page<Comment> page = new Page<>(baseRequestVO.getCurrent(), baseRequestVO.getSize());
            page.addOrder(OrderItem.desc("create_time"));
            
            Page<Comment> resultPage = lambdaQuery()
                .eq(Comment::getSource, baseRequestVO.getSource())
                .eq(Comment::getType, baseRequestVO.getCommentType())
                .eq(Comment::getParentCommentId, CommonConst.FIRST_COMMENT)
                .page(page);
            
            List<Comment> comments = resultPage.getRecords();
            if (CollectionUtils.isEmpty(comments)) {
                baseRequestVO.setRecords(new ArrayList<>());
                baseRequestVO.setTotal(0);
                return PoetryResult.success(baseRequestVO);
            }
            
            List<CommentVO> commentVOs = comments.stream().map(c -> {
                CommentVO commentVO = buildCommentVO(c);
                Page<Comment> childPage = new Page<>(1, 5);
                
                Page<Comment> childResultPage = lambdaQuery()
                    .eq(Comment::getSource, baseRequestVO.getSource())
                    .eq(Comment::getType, baseRequestVO.getCommentType())
                    .eq(Comment::getFloorCommentId, c.getId())
                    .orderByAsc(Comment::getCreateTime)
                    .page(childPage);
                
                List<Comment> childComments = childResultPage.getRecords();
                if (childComments != null) {
                    List<CommentVO> ccVO = childComments.stream()
                        .map(cc -> buildCommentVO(cc))
                        .collect(Collectors.toList());
                    
                    Page<CommentVO> commentVOPage = new Page<>(childPage.getCurrent(), childPage.getSize());
                    commentVOPage.setRecords(ccVO);
                    commentVOPage.setTotal(childPage.getTotal());
                    commentVO.setChildComments(commentVOPage);
                } else {
                    Page<CommentVO> commentVOPage = new Page<>(childPage.getCurrent(), childPage.getSize());
                    commentVOPage.setRecords(new ArrayList<>());
                    commentVOPage.setTotal(0);
                    commentVO.setChildComments(commentVOPage);
                }
                return commentVO;
            }).collect(Collectors.toList());
            
            baseRequestVO.setRecords(commentVOs);
            baseRequestVO.setTotal(resultPage.getTotal());
        } else {
            Page<Comment> page = new Page<>(baseRequestVO.getCurrent(), baseRequestVO.getSize());
            page.addOrder(OrderItem.asc("create_time"));
            
            Page<Comment> resultPage = lambdaQuery()
                .eq(Comment::getSource, baseRequestVO.getSource())
                .eq(Comment::getType, baseRequestVO.getCommentType())
                .eq(Comment::getFloorCommentId, baseRequestVO.getFloorCommentId())
                .page(page);
            
            List<Comment> childComments = resultPage.getRecords();
            if (CollectionUtils.isEmpty(childComments)) {
                baseRequestVO.setRecords(new ArrayList<>());
                baseRequestVO.setTotal(0);
                return PoetryResult.success(baseRequestVO);
            }
            
            List<CommentVO> ccVO = childComments.stream()
                .map(cc -> buildCommentVO(cc))
                .collect(Collectors.toList());
                
            baseRequestVO.setRecords(ccVO);
            baseRequestVO.setTotal(resultPage.getTotal());
        }
        
        return PoetryResult.success(baseRequestVO);
    }

    @Override
    public PoetryResult<Page> listAdminComment(BaseRequestVO baseRequestVO, Boolean isBoss) {
        LambdaQueryChainWrapper<Comment> wrapper = lambdaQuery();
        if (isBoss) {
            if (baseRequestVO.getSource() != null) {
                wrapper.eq(Comment::getSource, baseRequestVO.getSource());
            }
            if (StringUtils.hasText(baseRequestVO.getCommentType())) {
                wrapper.eq(Comment::getType, baseRequestVO.getCommentType());
            }
            Page<Comment> page = new Page<>(baseRequestVO.getCurrent(), baseRequestVO.getSize());
            wrapper.orderByDesc(Comment::getCreateTime).page(page);
            baseRequestVO.setRecords(page.getRecords());
            baseRequestVO.setTotal(page.getTotal());
        } else {
            List<Integer> userArticleIds = commonQuery.getUserArticleIds(PoetryUtil.getUserId());
            if (CollectionUtils.isEmpty(userArticleIds)) {
                baseRequestVO.setTotal(0);
                baseRequestVO.setRecords(new ArrayList());
            } else {
                if (baseRequestVO.getSource() != null) {
                    wrapper.eq(Comment::getSource, baseRequestVO.getSource()).eq(Comment::getType, CommentTypeEnum.COMMENT_TYPE_ARTICLE.getCode());
                } else {
                    wrapper.eq(Comment::getType, CommentTypeEnum.COMMENT_TYPE_ARTICLE.getCode()).in(Comment::getSource, userArticleIds);
                }
                Page<Comment> page = new Page<>(baseRequestVO.getCurrent(), baseRequestVO.getSize());
                wrapper.orderByDesc(Comment::getCreateTime).page(page);
                baseRequestVO.setRecords(page.getRecords());
                baseRequestVO.setTotal(page.getTotal());
            }
        }
        return PoetryResult.success(baseRequestVO);
    }

    private CommentVO buildCommentVO(Comment c) {
        CommentVO commentVO = new CommentVO();
        BeanUtils.copyProperties(c, commentVO);

        User user = commonQuery.getUser(commentVO.getUserId());
        if (user != null) {
            commentVO.setAvatar(user.getAvatar());
            commentVO.setUsername(user.getUsername());
        }

        if (!StringUtils.hasText(commentVO.getUsername())) {
            commentVO.setUsername(PoetryUtil.getRandomName(commentVO.getUserId().toString()));
        }

        if (commentVO.getParentUserId() != null) {
            User u = commonQuery.getUser(commentVO.getParentUserId());
            if (u != null) {
                commentVO.setParentUsername(u.getUsername());
            }
            if (!StringUtils.hasText(commentVO.getParentUsername())) {
                commentVO.setParentUsername(PoetryUtil.getRandomName(commentVO.getParentUserId().toString()));
            }
        }
        return commentVO;
    }
}
