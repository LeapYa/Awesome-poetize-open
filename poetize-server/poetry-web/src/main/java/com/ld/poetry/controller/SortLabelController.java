package com.ld.poetry.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.ld.poetry.aop.AuditLog;
import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.dao.LabelMapper;
import com.ld.poetry.dao.SortMapper;
import com.ld.poetry.entity.Label;
import com.ld.poetry.entity.Sort;
import com.ld.poetry.service.prerender.PrerenderFacade;
import com.ld.poetry.service.CacheService;
import com.ld.poetry.utils.CommonQuery;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import com.ld.poetry.utils.XssFilterUtil;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 分类标签 前端控制器
 * </p>
 *
 * @author sara
 * @since 2021-09-14
 */
@Slf4j
@RestController
@RequestMapping("/webInfo")
public class SortLabelController {

    @Autowired
    private SortMapper sortMapper;

    @Autowired
    private LabelMapper labelMapper;

    @Autowired
    private CommonQuery commonQuery;

    @Autowired
    private PrerenderFacade prerenderFacade;

    @Autowired
    private com.ld.poetry.service.ai.rag.RagSyncService ragSyncService;

    @Autowired
    private com.ld.poetry.service.SitemapService sitemapService;

    @Autowired
    private CacheService cacheService;

    /**
     * 获取分类标签信息
     */
    @GetMapping("/getSortInfo")
    public PoetryResult<List<Sort>> getSortInfo() {
        return PoetryResult.success(commonQuery.getSortInfo());
    }

    /**
     * 保存
     */
    @PostMapping("/saveSort")
    @LoginCheck(0)
    @AuditLog(action = "SORT_SAVE", targetType = "SORT", targetIdParam = "id", summary = "新增分类")
    public PoetryResult saveSort(@RequestBody Sort sort) {
        // XSS过滤处理
        String filteredSortName = StringUtils.hasText(sort.getSortName()) ? XssFilterUtil.clean(sort.getSortName()) : null;
        String filteredSortDescription = StringUtils.hasText(sort.getSortDescription()) ? XssFilterUtil.clean(sort.getSortDescription()) : null;
        
        if (!StringUtils.hasText(filteredSortName) || !StringUtils.hasText(filteredSortDescription)) {
            return PoetryResult.fail("分类名称和分类描述不能为空或包含不安全内容！");
        }

        if (sort.getPriority() == null) {
            return PoetryResult.fail("分类必须配置优先级！");
        }

        if (sort.getSortType() == null) {
            // 如果前端没传，默认设置为普通分类
            sort.setSortType(1);
        }

        // 检查分类名称是否已存在（唯一性校验）
        Sort existingSort = sortMapper.selectOne(new QueryWrapper<Sort>().eq("sort_name", filteredSortName));
        if (existingSort != null) {
            return PoetryResult.fail("分类名称已存在，请使用其他名称！");
        }

        // 使用过滤后的数据
        sort.setSortName(filteredSortName);
        sort.setSortDescription(filteredSortDescription);
        
        sortMapper.insert(sort);
        cacheService.evictSortArticleList();
        log.info("分类新增成功，分类名称: {}", sort.getSortName());

        // 分类新增后，清除sitemap缓存并重新渲染首页和分类索引页面
        try {
            // 1. 更新sitemap并推送（新增分类会影响sitemap中的分类页面）
            if (sitemapService != null) {
                sitemapService.updateSitemapAndPush("分类新增 (ID=" + sort.getId() + ", 名称=" + sort.getSortName() + ")");
                log.info("分类新增后已更新sitemap并推送到搜索引擎");
            }
            
            // 2. 重新渲染页面
            prerenderFacade.refreshSortHierarchy(sort.getId());
        } catch (Exception e) {
            // 预渲染失败不影响主流程
            log.warn("分类新增后sitemap更新和页面预渲染失败", e);
        }
        
        ragSyncService.rebuildAllAsync();
        return PoetryResult.success();
    }


    /**
     * 删除
     */
    @GetMapping("/deleteSort")
    @LoginCheck(0)
    @AuditLog(action = "SORT_DELETE", targetType = "SORT", targetIdParam = "id", summary = "删除分类")
    public PoetryResult deleteSort(@RequestParam("id") Integer id) {
        sortMapper.deleteById(id);
        cacheService.evictSortArticleList();
        log.info("分类删除成功，分类ID: {}", id);

        // 分类删除后，清除sitemap缓存，删除对应分类页面的预渲染文件，并重新渲染首页和分类索引页面
        try {
            // 1. 更新sitemap并推送（删除分类会影响sitemap中的分类页面）
            if (sitemapService != null) {
                sitemapService.updateSitemapAndPush("分类删除 (ID=" + id + ")");
                log.info("分类删除后已更新sitemap并推送到搜索引擎");
            }
            
            // 2. 删除预渲染文件并重新渲染页面
            prerenderFacade.deleteSortHierarchy(id);
        } catch (Exception e) {
            // 预渲染失败不影响主流程
            log.warn("分类删除后sitemap更新和页面预渲染失败", e);
        }

        ragSyncService.rebuildAllAsync();
        return PoetryResult.success();
    }


    /**
     * 更新
     */
    @PostMapping("/updateSort")
    @LoginCheck(0)
    @AuditLog(action = "SORT_UPDATE", targetType = "SORT", targetIdParam = "id", summary = "更新分类")
    public PoetryResult updateSort(@RequestBody Sort sort) {
        // XSS过滤处理
        String filteredSortName = StringUtils.hasText(sort.getSortName()) ? XssFilterUtil.clean(sort.getSortName()) : null;
        String filteredSortDescription = StringUtils.hasText(sort.getSortDescription()) ? XssFilterUtil.clean(sort.getSortDescription()) : null;
        
        if (!StringUtils.hasText(filteredSortName) || !StringUtils.hasText(filteredSortDescription)) {
            return PoetryResult.fail("分类名称和分类描述不能为空或包含不安全内容！");
        }

        if (sort.getPriority() == null) {
            return PoetryResult.fail("分类必须配置优先级！");
        }

        // 检查分类名称是否已存在（唯一性校验），排除当前分类
        Sort existingSort = sortMapper.selectOne(new QueryWrapper<Sort>()
                .eq("sort_name", filteredSortName)
                .ne("id", sort.getId()));
        if (existingSort != null) {
            return PoetryResult.fail("分类名称已存在，请使用其他名称！");
        }

        // 使用过滤后的数据
        sort.setSortName(filteredSortName);
        sort.setSortDescription(filteredSortDescription);
        
        sortMapper.updateById(sort);
        cacheService.evictSortArticleList();
        log.info("分类更新成功，分类ID: {}", sort.getId());

        // 分类更新后，清除sitemap缓存并重新渲染对应分类页面、首页和分类索引页面
        try {
            // 1. 更新sitemap并推送（分类信息变更会影响sitemap中的分类页面）
            if (sitemapService != null) {
                sitemapService.updateSitemapAndPush("分类更新 (ID=" + sort.getId() + ", 名称=" + sort.getSortName() + ")");
                log.info("分类更新后已更新sitemap并推送到搜索引擎");
            }
            
            // 2. 重新渲染页面
            prerenderFacade.refreshSortHierarchy(sort.getId());
        } catch (Exception e) {
            // 预渲染失败不影响主流程
            log.warn("分类更新后sitemap更新和页面预渲染失败", e);
        }

        ragSyncService.rebuildAllAsync();
        return PoetryResult.success();
    }


    /**
     * 查询List
     */
    @GetMapping("/listSort")
    public PoetryResult<List<Sort>> listSort() {
        return PoetryResult.success(new LambdaQueryChainWrapper<>(sortMapper).list());
    }


    /**
     * 保存
     */
    @PostMapping("/saveLabel")
    @LoginCheck(0)
    @AuditLog(action = "LABEL_SAVE", targetType = "LABEL", targetIdParam = "id", summary = "新增标签")
    public PoetryResult saveLabel(@RequestBody Label label) {
        // XSS过滤处理
        String filteredLabelName = StringUtils.hasText(label.getLabelName()) ? XssFilterUtil.clean(label.getLabelName()) : null;
        String filteredLabelDescription = StringUtils.hasText(label.getLabelDescription()) ? XssFilterUtil.clean(label.getLabelDescription()) : null;
        
        if (!StringUtils.hasText(filteredLabelName) || !StringUtils.hasText(filteredLabelDescription) || label.getSortId() == null) {
            return PoetryResult.fail("标签名称和标签描述和分类Id不能为空或包含不安全内容！");
        }

        // 检查在同一分类下标签名称是否已存在（唯一性校验）
        Label existingLabel = labelMapper.selectOne(new QueryWrapper<Label>()
                .eq("label_name", filteredLabelName)
                .eq("sort_id", label.getSortId()));
        if (existingLabel != null) {
            return PoetryResult.fail("该分类下已存在相同的标签名称，请使用其他名称！");
        }

        // 使用过滤后的数据
        label.setLabelName(filteredLabelName);
        label.setLabelDescription(filteredLabelDescription);
        
        labelMapper.insert(label);
        cacheService.evictSortArticleList();
        log.info("标签新增成功，标签名称: {}", label.getLabelName());

        // 标签新增后，清除sitemap缓存并重新渲染对应分类页面
        try {
            // 1. 清除sitemap缓存（标签信息可能影响sitemap生成）
            if (sitemapService != null) {
                sitemapService.clearSitemapCache();
                log.info("标签新增后已清除sitemap缓存");
            }
            
            // 2. 重新渲染页面
            prerenderFacade.refreshLabelHierarchy(label.getSortId(), label.getId(), null);
        } catch (Exception e) {
            // 预渲染失败不影响主流程
            log.warn("标签新增后sitemap更新和页面预渲染失败", e);
        }
        
        ragSyncService.rebuildAllAsync();
        return PoetryResult.success();
    }


    /**
     * 删除
     */
    @GetMapping("/deleteLabel")
    @LoginCheck(0)
    @AuditLog(action = "LABEL_DELETE", targetType = "LABEL", targetIdParam = "id", summary = "删除标签")
    public PoetryResult deleteLabel(@RequestParam("id") Integer id) {
        // 先获取标签信息以获得分类ID
        Label label = labelMapper.selectById(id);
        
        labelMapper.deleteById(id);
        cacheService.evictSortArticleList();
        log.info("标签删除成功，标签ID: {}", id);

        // 标签删除后，清除sitemap缓存并重新渲染对应分类页面
        if (label != null && label.getSortId() != null) {
            try {
                // 1. 清除sitemap缓存（标签删除可能影响sitemap生成）
                if (sitemapService != null) {
                    sitemapService.clearSitemapCache();
                    log.info("标签删除后已清除sitemap缓存");
                }
                
                // 2. 重新渲染页面
                prerenderFacade.deleteLabelHierarchy(label.getSortId(), label.getId());
            } catch (Exception e) {
                // 预渲染失败不影响主流程
                log.warn("标签删除后sitemap更新和页面预渲染失败", e);
            }
        }
        
        ragSyncService.rebuildAllAsync();
        return PoetryResult.success();
    }


    /**
     * 更新
     */
    @PostMapping("/updateLabel")
    @LoginCheck(0)
    @AuditLog(action = "LABEL_UPDATE", targetType = "LABEL", targetIdParam = "id", summary = "更新标签")
    public PoetryResult updateLabel(@RequestBody Label label) {
        Label existingLabelRecord = labelMapper.selectById(label.getId());

        // XSS过滤处理
        String filteredLabelName = StringUtils.hasText(label.getLabelName()) ? XssFilterUtil.clean(label.getLabelName()) : null;
        String filteredLabelDescription = StringUtils.hasText(label.getLabelDescription()) ? XssFilterUtil.clean(label.getLabelDescription()) : null;
        
        if (!StringUtils.hasText(filteredLabelName) || !StringUtils.hasText(filteredLabelDescription) || label.getSortId() == null) {
            return PoetryResult.fail("标签名称和标签描述和分类Id不能为空或包含不安全内容！");
        }

        // 检查在同一分类下标签名称是否已存在（唯一性校验），排除当前标签
        Label existingLabel = labelMapper.selectOne(new QueryWrapper<Label>()
                .eq("label_name", filteredLabelName)
                .eq("sort_id", label.getSortId())
                .ne("id", label.getId()));
        if (existingLabel != null) {
            return PoetryResult.fail("该分类下已存在相同的标签名称，请使用其他名称！");
        }

        // 使用过滤后的数据
        label.setLabelName(filteredLabelName);
        label.setLabelDescription(filteredLabelDescription);
        
        labelMapper.updateById(label);
        cacheService.evictSortArticleList();
        log.info("标签更新成功，标签ID: {}", label.getId());

        // 标签更新后，清除sitemap缓存并重新渲染对应分类页面
        try {
            // 1. 清除sitemap缓存（标签信息变更可能影响sitemap生成）
            if (sitemapService != null) {
                sitemapService.clearSitemapCache();
                log.info("标签更新后已清除sitemap缓存");
            }
            
            // 2. 重新渲染页面
            prerenderFacade.refreshLabelHierarchy(label.getSortId(), label.getId(),
                    existingLabelRecord == null ? null : existingLabelRecord.getSortId());
        } catch (Exception e) {
            // 预渲染失败不影响主流程
            log.warn("标签更新后sitemap更新和页面预渲染失败", e);
        }

        ragSyncService.rebuildAllAsync();
        return PoetryResult.success();
    }


    /**
     * 查询List
     */
    @GetMapping("/listLabel")
    public PoetryResult<List<Label>> listLabel() {
        return PoetryResult.success(new LambdaQueryChainWrapper<>(labelMapper).list());
    }


    /**
     * 查询List
     */
    @GetMapping("/listSortAndLabel")
    public PoetryResult<Map> listSortAndLabel() {
        Map<String, List> map = new HashMap<>();
        map.put("sorts", new LambdaQueryChainWrapper<>(sortMapper).list());
        map.put("labels", new LambdaQueryChainWrapper<>(labelMapper).list());
        return PoetryResult.success(map);
    }
}
