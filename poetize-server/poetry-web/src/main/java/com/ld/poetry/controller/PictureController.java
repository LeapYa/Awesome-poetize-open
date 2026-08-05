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
import com.ld.poetry.utils.PoetryUtil;
import com.ld.poetry.vo.ResourcePathVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 资源聚合里的图片，其他接口在ResourceAggregationController
 * </p>
 *
 * @author sara
 * @since 2021-09-14
 */
@RestController
@RequestMapping("/webInfo")
public class PictureController {

    @Autowired
    private ResourcePathMapper resourcePathMapper;

    @Autowired
    private CacheService cacheService;

    /**
     * 查询爱情
     */
    @GetMapping("/listAdminLovePhoto")
    public PoetryResult<List<Map<String, Object>>> listAdminLovePhoto() {
        // 分类聚合结果走 Redis 缓存（旅拍/恋爱笔记页高频读，资源变更时由 ResourceAggregationController 主动 evict）
        Object cached = cacheService.get(CacheConstants.LOVE_PHOTO_CLASSIFY_COUNT_KEY);
        if (cached instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cachedList = (List<Map<String, Object>>) cached;
            return PoetryResult.success(cachedList);
        }

        QueryWrapper<ResourcePath> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("classify, count(*) as count")
                .eq("status", Boolean.TRUE)
                .eq("remark", PoetryUtil.getAdminUser().getId().toString())
                .eq("type", CommonConst.RESOURCE_PATH_TYPE_LOVE_PHOTO)
                .groupBy("classify");
        List<Map<String, Object>> maps = resourcePathMapper.selectMaps(queryWrapper);
        cacheService.set(CacheConstants.LOVE_PHOTO_CLASSIFY_COUNT_KEY, maps, CacheConstants.LONG_EXPIRE_TIME);

        return PoetryResult.success(maps);
    }

    /**
     * 保存爱情
     */
    @LoginCheck
    @SaveCheck
    @PostMapping("/saveLovePhoto")
    public PoetryResult saveLovePhoto(@RequestBody ResourcePathVO resourcePathVO) {
        if (!StringUtils.hasText(resourcePathVO.getClassify()) || !StringUtils.hasText(resourcePathVO.getCover()) ||
                !StringUtils.hasText(resourcePathVO.getTitle())) {
            return PoetryResult.fail("信息不全！");
        }
        ResourcePath lovePhoto = new ResourcePath();
        lovePhoto.setClassify(resourcePathVO.getClassify());
        lovePhoto.setTitle(resourcePathVO.getTitle());
        lovePhoto.setCover(resourcePathVO.getCover());
        lovePhoto.setRemark(PoetryUtil.getUserId().toString());
        lovePhoto.setType(CommonConst.RESOURCE_PATH_TYPE_LOVE_PHOTO);
        lovePhoto.setStatus(Boolean.FALSE);
        resourcePathMapper.insert(lovePhoto);
        return PoetryResult.success();
    }
}
