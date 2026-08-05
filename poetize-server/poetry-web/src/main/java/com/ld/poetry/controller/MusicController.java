package com.ld.poetry.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.aop.SaveCheck;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.constants.CacheConstants;
import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.dao.ResourcePathMapper;
import com.ld.poetry.entity.ResourcePath;
import com.ld.poetry.service.CacheService;
import com.ld.poetry.vo.ResourcePathVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 资源聚合里的音乐，其他接口在ResourceAggregationController
 * </p>
 *
 * @author sara
 * @since 2021-09-14
 */
@RestController
@RequestMapping("/webInfo")
public class MusicController {

    @Autowired
    private ResourcePathMapper resourcePathMapper;

    @Autowired
    private CacheService cacheService;

    /**
     * 查询音乐
     */
    @GetMapping("/listFunny")
    public PoetryResult<List<Map<String, Object>>> listFunny() {
        // 分类聚合结果走 Redis 缓存（趣味页高频读，资源变更时由 ResourceAggregationController 主动 evict）
        Object cached = cacheService.get(CacheConstants.FUNNY_CLASSIFY_COUNT_KEY);
        if (cached instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cachedList = (List<Map<String, Object>>) cached;
            return PoetryResult.success(cachedList);
        }

        QueryWrapper<ResourcePath> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("classify, count(*) as count")
                .eq("status", Boolean.TRUE)
                .eq("type", CommonConst.RESOURCE_PATH_TYPE_FUNNY)
                .groupBy("classify");
        List<Map<String, Object>> maps = resourcePathMapper.selectMaps(queryWrapper);
        cacheService.set(CacheConstants.FUNNY_CLASSIFY_COUNT_KEY, maps, CacheConstants.LONG_EXPIRE_TIME);
        return PoetryResult.success(maps);
    }

    /**
     * 保存音乐
     */
    @LoginCheck
    @SaveCheck
    @PostMapping("/saveFunny")
    public PoetryResult saveFunny(@RequestBody ResourcePathVO resourcePathVO) {
        if (!StringUtils.hasText(resourcePathVO.getClassify()) || !StringUtils.hasText(resourcePathVO.getCover()) ||
                !StringUtils.hasText(resourcePathVO.getUrl()) || !StringUtils.hasText(resourcePathVO.getTitle())) {
            return PoetryResult.fail("信息不全！");
        }
        ResourcePath funny = new ResourcePath();
        funny.setClassify(resourcePathVO.getClassify());
        funny.setTitle(resourcePathVO.getTitle());
        funny.setCover(resourcePathVO.getCover());
        funny.setUrl(resourcePathVO.getUrl());
        funny.setType(CommonConst.RESOURCE_PATH_TYPE_FUNNY);
        funny.setStatus(Boolean.FALSE);
        resourcePathMapper.insert(funny);
        return PoetryResult.success();
    }
}
