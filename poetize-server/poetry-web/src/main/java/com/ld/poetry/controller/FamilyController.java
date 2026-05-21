package com.ld.poetry.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ld.poetry.aop.AuditLog;
import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.constants.CacheConstants;
import com.ld.poetry.entity.Family;
import com.ld.poetry.service.CacheService;
import com.ld.poetry.service.FamilyService;
import com.ld.poetry.utils.CommonQuery;
import com.ld.poetry.utils.PoetryUtil;
import com.ld.poetry.vo.BaseRequestVO;
import com.ld.poetry.vo.FamilyVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

/**
 * <p>
 * 家庭信息 前端控制器
 * </p>
 *
 * @author sara
 * @since 2023-01-03
 */
@RestController
@RequestMapping("/family")
@Slf4j
public class FamilyController {

    @Autowired
    private FamilyService familyService;

    @Autowired
    private CommonQuery commonQuery;

    @Autowired
    private CacheService cacheService;

    /**
     * 保存
     */
    @PostMapping("/saveFamily")
    @LoginCheck
    public PoetryResult saveFamily(@Validated @RequestBody FamilyVO familyVO) {
        Integer userId = PoetryUtil.getUserId();
        familyVO.setUserId(userId);
        Family oldFamily = familyService.lambdaQuery().select(Family::getId).eq(Family::getUserId, userId).one();
        Family family = new Family();
        BeanUtils.copyProperties(familyVO, family);
        if (userId.intValue() == PoetryUtil.getAdminUser().getId().intValue()) {
            family.setStatus(Boolean.TRUE);
        } else {
            family.setStatus(Boolean.FALSE);
        }

        if (oldFamily != null) {
            family.setId(oldFamily.getId());
            familyService.updateById(family);
        } else {
            family.setId(null);
            familyService.save(family);
        }
        try {
            // 如果是管理员，缓存管理员家庭信息
            if (userId.intValue() == PoetryUtil.getAdminUser().getId().intValue()) {
                String adminFamilyKey = CacheConstants.CACHE_PREFIX + "admin:family";
                cacheService.set(adminFamilyKey, family, CacheConstants.LONG_EXPIRE_TIME);
            }

            // 清理家庭列表缓存
            cacheService.deleteKey(CacheConstants.FAMILY_LIST_KEY);
        } catch (Exception e) {
            log.error("保存家庭信息后更新缓存失败: userId={}", userId, e);
        }

        return PoetryResult.success();
    }

    /**
     * 删除
     */
    @GetMapping("/deleteFamily")
    @LoginCheck(0)
    @AuditLog(action = "LOVE_DELETE", targetType = "LOVE", targetIdParam = "id", summary = "删除表白墙")
    public PoetryResult deleteFamily(@RequestParam("id") Integer id) {
        familyService.removeById(id);

        try {
            // 清理家庭列表缓存
            cacheService.deleteKey(CacheConstants.FAMILY_LIST_KEY);
        } catch (Exception e) {
            log.error("删除家庭信息后清理缓存失败: id={}", id, e);
        }

        return PoetryResult.success();
    }

    /**
     * 获取
     */
    @GetMapping("/getFamily")
    @LoginCheck
    public PoetryResult<FamilyVO> getFamily() {
        Integer userId = PoetryUtil.getUserId();
        Family family = familyService.lambdaQuery().eq(Family::getUserId, userId).one();
        if (family == null) {
            return PoetryResult.success();
        } else {
            FamilyVO familyVO = new FamilyVO();
            BeanUtils.copyProperties(family, familyVO);
            return PoetryResult.success(familyVO);
        }
    }

    /**
     * 获取
     */
    @GetMapping("/getAdminFamily")
    public PoetryResult<FamilyVO> getAdminFamily() {
        try {
            // 从Redis缓存获取管理员家庭信息
            String adminFamilyKey = CacheConstants.CACHE_PREFIX + "admin:family";
            Object cached = cacheService.get(adminFamilyKey);
            Family family = null;

            if (cached instanceof Family) {
                family = (Family) cached;
            } else {
                // 如果缓存中没有，从数据库查询管理员的家庭信息
                Integer adminUserId = PoetryUtil.getAdminUser().getId();
                family = familyService.lambdaQuery().eq(Family::getUserId, adminUserId).one();
                if (family != null) {
                    cacheService.set(adminFamilyKey, family, CacheConstants.LONG_EXPIRE_TIME);
                }
            }

            if (family == null) {
                return PoetryResult.fail("请初始化表白墙");
            }

            FamilyVO familyVO = new FamilyVO();
            BeanUtils.copyProperties(family, familyVO);
            return PoetryResult.success(familyVO);
        } catch (Exception e) {
            log.error("获取管理员家庭信息失败", e);
            return PoetryResult.fail("获取家庭信息失败");
        }
    }

    /**
     * 查询随机家庭
     */
    @GetMapping("/listRandomFamily")
    public PoetryResult<List<FamilyVO>> listRandomFamily(@RequestParam(value = "size", defaultValue = "10") Integer size) {
        List<FamilyVO> familyList = commonQuery.getFamilyList();
        if (familyList.size() > size) {
            Collections.shuffle(familyList);
            familyList = familyList.subList(0, size);
        }
        return PoetryResult.success(familyList);
    }

    /**
     * 查询
     */
    @PostMapping("/listFamily")
    @LoginCheck(0)
    public PoetryResult<Page> listFamily(@RequestBody BaseRequestVO baseRequestVO) {
        Page<Family> page = new Page<>(baseRequestVO.getCurrent(), baseRequestVO.getSize());
        Page<Family> result = familyService.lambdaQuery()
                .eq(baseRequestVO.getStatus() != null, Family::getStatus, baseRequestVO.getStatus())
                .orderByDesc(Family::getCreateTime).page(page);
        return PoetryResult.success(result);
    }

    /**
     * 修改状态
     */
    @GetMapping("/changeLoveStatus")
    @LoginCheck(0)
    @AuditLog(action = "LOVE_STATUS_CHANGE", targetType = "LOVE", targetIdParam = "id", summary = "修改表白墙状态")
    public PoetryResult changeLoveStatus(@RequestParam("id") Integer id, @RequestParam("flag") Boolean flag) {
        familyService.lambdaUpdate().eq(Family::getId, id).set(Family::getStatus, flag).update();

        try {
            // 清理家庭列表缓存
            cacheService.deleteKey(CacheConstants.FAMILY_LIST_KEY);
        } catch (Exception e) {
            log.error("修改表白状态后清理缓存失败: id={}, flag={}", id, flag, e);
        }

        return PoetryResult.success();
    }
}
