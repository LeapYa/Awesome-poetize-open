package com.ld.poetry.controller;


import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.ld.poetry.aop.AuditLog;
import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.aop.SaveCheck;
import com.ld.poetry.dao.ArticleMapper;
import com.ld.poetry.entity.Article;
import com.ld.poetry.entity.WeiYan;
import com.ld.poetry.service.CacheService;
import com.ld.poetry.service.WeiYanService;
import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.enums.PoetryEnum;
import com.ld.poetry.utils.PoetryUtil;
import com.ld.poetry.utils.StringUtil;
import com.ld.poetry.utils.XssFilterUtil;
import com.ld.poetry.vo.BaseRequestVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;

/**
 * <p>
 * 微言表 前端控制器
 * </p>
 *
 * @author sara
 * @since 2021-10-26
 */
@SuppressWarnings("unchecked")
@RestController
@RequestMapping("/weiYan")
public class WeiYanController {

    @Autowired
    private WeiYanService weiYanService;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private CacheService cacheService;

    /**
     * 保存
     */
    @PostMapping("/saveWeiYan")
    @LoginCheck
    @SaveCheck
    @AuditLog(action = "WEIYAN_SAVE", targetType = "WEIYAN", summary = "保存微言")
    public PoetryResult saveWeiYan(@RequestBody WeiYan weiYanVO) {
        if (!StringUtils.hasText(weiYanVO.getContent())) {
            return PoetryResult.fail("微言不能为空！");
        }

        // XSS过滤处理
        String content = XssFilterUtil.clean(weiYanVO.getContent());
        if (!StringUtils.hasText(content)) {
            return PoetryResult.fail("微言内容不合法！");
        }
        weiYanVO.setContent(content);

        WeiYan weiYan = new WeiYan();
        weiYan.setUserId(PoetryUtil.getUserId());
        weiYan.setContent(weiYanVO.getContent());
        weiYan.setIsPublic(weiYanVO.getIsPublic());
        weiYan.setType(CommonConst.WEIYAN_TYPE_FRIEND);
        weiYanService.save(weiYan);
        return PoetryResult.success();
    }


    /**
     * 保存
     */
    @PostMapping("/saveNews")
    @LoginCheck
    @AuditLog(action = "WEIYAN_NEWS_SAVE", targetType = "WEIYAN", targetIdParam = "source", summary = "保存文章动态")
    public PoetryResult saveNews(@RequestBody WeiYan weiYanVO) {
        if (!StringUtils.hasText(weiYanVO.getContent()) || weiYanVO.getSource() == null) {
            return PoetryResult.fail("信息不全！");
        }

        // XSS过滤处理
        String content = XssFilterUtil.clean(weiYanVO.getContent());
        if (!StringUtils.hasText(content)) {
            return PoetryResult.fail("内容不合法！");
        }
        weiYanVO.setContent(content);

        if (weiYanVO.getCreateTime() == null) {
            weiYanVO.setCreateTime(LocalDateTime.now());
        }

        Integer userId = PoetryUtil.getUserId();

        LambdaQueryChainWrapper<Article> wrapper = new LambdaQueryChainWrapper<>(articleMapper);
        Long count = wrapper.eq(Article::getId, weiYanVO.getSource()).eq(Article::getUserId, userId).count();

        if (count == null || count < 1) {
            return PoetryResult.fail("来源不存在！");
        }

        WeiYan weiYan = new WeiYan();
        weiYan.setUserId(userId);
        weiYan.setContent(weiYanVO.getContent());
        weiYan.setIsPublic(Boolean.TRUE);
        weiYan.setSource(weiYanVO.getSource());
        weiYan.setCreateTime(weiYanVO.getCreateTime());
        weiYan.setType(CommonConst.WEIYAN_TYPE_NEWS);
        weiYanService.save(weiYan);
        // 保存文章动态后清理对应文章的列表缓存
        cacheService.evictWeiYanNewsList(weiYanVO.getSource());
        return PoetryResult.success();
    }

    /**
     * 查询List
     */
    @PostMapping("/listNews")
    public PoetryResult<BaseRequestVO> listNews(@RequestBody BaseRequestVO baseRequestVO) {
        if (baseRequestVO.getSource() == null) {
            return PoetryResult.fail("来源不能为空！");
        }

        // 命中缓存直接返回(文章页 size=9999 拉全量, 缓存键按 source 维度)
        Object cached = cacheService.getCachedWeiYanNewsList(baseRequestVO.getSource());
        if (cached instanceof BaseRequestVO) {
            BaseRequestVO cachedVO = (BaseRequestVO) cached;
            // 浅拷贝 records, 避免上层修改污染缓存
            baseRequestVO.setRecords(new ArrayList<>(cachedVO.getRecords()));
            baseRequestVO.setTotal(cachedVO.getTotal());
            return PoetryResult.success(baseRequestVO);
        }

        LambdaQueryChainWrapper<WeiYan> lambdaQuery = weiYanService.lambdaQuery();
        lambdaQuery.eq(WeiYan::getType, CommonConst.WEIYAN_TYPE_NEWS);
        lambdaQuery.eq(WeiYan::getSource, baseRequestVO.getSource());
        lambdaQuery.eq(WeiYan::getIsPublic, PoetryEnum.PUBLIC.getCode());

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<WeiYan> page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(baseRequestVO.getCurrent(), baseRequestVO.getSize());
        lambdaQuery.page(page);
        baseRequestVO.setRecords(page.getRecords());
        baseRequestVO.setTotal(page.getTotal());

        // 写入缓存(浅拷贝 records, 避免后续被修改污染缓存)
        BaseRequestVO cacheSnapshot = new BaseRequestVO();
        cacheSnapshot.setSource(baseRequestVO.getSource());
        cacheSnapshot.setCurrent(baseRequestVO.getCurrent());
        cacheSnapshot.setSize(baseRequestVO.getSize());
        cacheSnapshot.setRecords(new ArrayList<>(baseRequestVO.getRecords()));
        cacheSnapshot.setTotal(baseRequestVO.getTotal());
        cacheService.cacheWeiYanNewsList(baseRequestVO.getSource(), cacheSnapshot);

        return PoetryResult.success(baseRequestVO);
    }

    /**
     * 删除
     */
    @GetMapping("/deleteWeiYan")
    @LoginCheck
    @AuditLog(action = "WEIYAN_DELETE", targetType = "WEIYAN", targetIdParam = "id", summary = "删除微言")
    public PoetryResult deleteWeiYan(@RequestParam("id") Integer id) {
        Integer userId = PoetryUtil.getUserId();
        // 先查出 WeiYan 的 source, 用于删除后 evict 对应 source 的 listNews 缓存
        WeiYan existing = weiYanService.lambdaQuery()
                .select(WeiYan::getSource, WeiYan::getType)
                .eq(WeiYan::getId, id)
                .eq(WeiYan::getUserId, userId)
                .one();
        weiYanService.lambdaUpdate().eq(WeiYan::getId, id)
                .eq(WeiYan::getUserId, userId)
                .remove();
        // 仅当是 NEWS 类型(有 source)时才需要 evict listNews 缓存
        if (existing != null && existing.getSource() != null
                && CommonConst.WEIYAN_TYPE_NEWS.equals(existing.getType())) {
            cacheService.evictWeiYanNewsList(existing.getSource());
        }
        return PoetryResult.success();
    }


    /**
     * 查询List
     */
    @PostMapping("/listWeiYan")
    public PoetryResult<BaseRequestVO> listWeiYan(@RequestBody BaseRequestVO baseRequestVO) {
        LambdaQueryChainWrapper<WeiYan> lambdaQuery = weiYanService.lambdaQuery();
        lambdaQuery.eq(WeiYan::getType, CommonConst.WEIYAN_TYPE_FRIEND);
        if (baseRequestVO.getUserId() == null) {
            if (PoetryUtil.getUserId() != null) {
                lambdaQuery.eq(WeiYan::getUserId, PoetryUtil.getUserId());
            } else {
                lambdaQuery.eq(WeiYan::getIsPublic, PoetryEnum.PUBLIC.getCode());
                lambdaQuery.eq(WeiYan::getUserId, PoetryUtil.getAdminUser().getId());
            }
        } else {
            if (!baseRequestVO.getUserId().equals(PoetryUtil.getUserId())) {
                lambdaQuery.eq(WeiYan::getIsPublic, PoetryEnum.PUBLIC.getCode());
            }
            lambdaQuery.eq(WeiYan::getUserId, baseRequestVO.getUserId());
        }

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<WeiYan> page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(baseRequestVO.getCurrent(), baseRequestVO.getSize());
        lambdaQuery.orderByDesc(WeiYan::getCreateTime).page(page);
        baseRequestVO.setRecords(page.getRecords());
        baseRequestVO.setTotal(page.getTotal());
        return PoetryResult.success(baseRequestVO);
    }
}
