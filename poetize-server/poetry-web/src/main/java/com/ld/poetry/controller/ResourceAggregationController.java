package com.ld.poetry.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.dao.ResourcePathMapper;
import com.ld.poetry.entity.ResourcePath;
import com.ld.poetry.utils.PoetryUtil;
import com.ld.poetry.service.CacheService;
import com.ld.poetry.service.prerender.PrerenderFacade;
import com.ld.poetry.utils.XssFilterUtil;
import com.ld.poetry.vo.BaseRequestVO;
import com.ld.poetry.vo.ResourcePathVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 资源聚合 前端控制器
 * </p>
 *
 * @author sara
 * @since 2021-09-14
 */
@SuppressWarnings("unchecked")
@RestController
@RequestMapping("/webInfo")
public class ResourceAggregationController {

    @Autowired
    private ResourcePathMapper resourcePathMapper;

    @Autowired
    private com.ld.poetry.utils.mail.MailUtil mailUtil;

    @Autowired
    private PrerenderFacade prerenderFacade;

    @Autowired
    private CacheService cacheService;

    /**
     * 保存
     */
    @LoginCheck(0)
    @PostMapping("/saveResourcePath")
    public PoetryResult saveResourcePath(@RequestBody ResourcePathVO resourcePathVO) {
        // XSS过滤处理
        String filteredTitle = StringUtils.hasText(resourcePathVO.getTitle()) ? XssFilterUtil.clean(resourcePathVO.getTitle()) : null;
        String filteredIntroduction = StringUtils.hasText(resourcePathVO.getIntroduction()) ? XssFilterUtil.clean(resourcePathVO.getIntroduction()) : null;
        String filteredUrl = StringUtils.hasText(resourcePathVO.getUrl()) ? XssFilterUtil.clean(resourcePathVO.getUrl()) : null;
        String filteredCover = StringUtils.hasText(resourcePathVO.getCover()) ? XssFilterUtil.clean(resourcePathVO.getCover()) : null;
        String filteredClassify = StringUtils.hasText(resourcePathVO.getClassify()) ? XssFilterUtil.clean(resourcePathVO.getClassify()) : null;
        String filteredRemark = StringUtils.hasText(resourcePathVO.getRemark()) ? XssFilterUtil.clean(resourcePathVO.getRemark()) : null;
        String filteredExtraBackground = StringUtils.hasText(resourcePathVO.getExtraBackground()) ? XssFilterUtil.clean(resourcePathVO.getExtraBackground()) : null;
        String filteredBtnWidth = StringUtils.hasText(resourcePathVO.getBtnWidth()) ? XssFilterUtil.clean(resourcePathVO.getBtnWidth()) : null;
        String filteredBtnHeight = StringUtils.hasText(resourcePathVO.getBtnHeight()) ? XssFilterUtil.clean(resourcePathVO.getBtnHeight()) : null;
        String filteredBtnRadius = StringUtils.hasText(resourcePathVO.getBtnRadius()) ? XssFilterUtil.clean(resourcePathVO.getBtnRadius()) : null;
        
        // 侧边栏背景类型的特殊验证
        if (CommonConst.RESOURCE_PATH_TYPE_ASIDE_BACKGROUND.equals(resourcePathVO.getType())) {
            if (!StringUtils.hasText(filteredCover)) {
                return PoetryResult.fail("侧边栏背景图片/CSS代码不能为空或包含不安全内容！");
            }
        } else {
            if (!StringUtils.hasText(filteredTitle) || !StringUtils.hasText(resourcePathVO.getType())) {
                return PoetryResult.fail("标题和资源类型不能为空或包含不安全内容！");
            }
        }
        
        // 本站信息和侧边栏背景类型的特殊验证：只能有一条记录
        if (CommonConst.RESOURCE_PATH_TYPE_SITE_INFO.equals(resourcePathVO.getType()) ||
            CommonConst.RESOURCE_PATH_TYPE_ASIDE_BACKGROUND.equals(resourcePathVO.getType())) {
            LambdaQueryChainWrapper<ResourcePath> wrapper = new LambdaQueryChainWrapper<>(resourcePathMapper);
            long count = wrapper.eq(ResourcePath::getType, resourcePathVO.getType()).count();
            if (count > 0) {
                String typeName = CommonConst.RESOURCE_PATH_TYPE_SITE_INFO.equals(resourcePathVO.getType()) ? "本站信息" : "侧边栏背景";
                return PoetryResult.fail(typeName + "只能有一条记录，请编辑现有记录！");
            }
        }
        
        if (CommonConst.RESOURCE_PATH_TYPE_LOVE_PHOTO.equals(resourcePathVO.getType())) {
            resourcePathVO.setRemark(PoetryUtil.getAdminUser().getId().toString());
        }
        if (CommonConst.RESOURCE_PATH_TYPE_SITE_INFO.equals(resourcePathVO.getType())) {
            resourcePathVO.setUrl(null);
        }
        
        // 侧边栏背景：自动设置标题，并将额外背景层存储到remark
        if (CommonConst.RESOURCE_PATH_TYPE_ASIDE_BACKGROUND.equals(resourcePathVO.getType())) {
            resourcePathVO.setTitle("侧边栏背景");
            if (StringUtils.hasText(filteredExtraBackground)) {
                // 转义双引号和反斜杠
                String escapedExtra = filteredExtraBackground
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"");
                resourcePathVO.setRemark("{\"extraBackground\":\"" + escapedExtra + "\"}");
            } else {
                resourcePathVO.setRemark(null); // 没有额外背景则不设置remark
            }
        }
        
        // 快捷入口和联系方式：将样式转换为JSON存储到remark字段
        if (CommonConst.RESOURCE_PATH_TYPE_QUICK_ENTRY.equals(resourcePathVO.getType()) || 
            CommonConst.RESOURCE_PATH_TYPE_CONTACT.equals(resourcePathVO.getType())) {
            StringBuilder jsonBuilder = new StringBuilder("{");
            if (StringUtils.hasText(filteredBtnWidth)) {
                jsonBuilder.append("\"btnWidth\":\"").append(filteredBtnWidth).append("\",");
            }
            if (StringUtils.hasText(filteredBtnHeight)) {
                jsonBuilder.append("\"btnHeight\":\"").append(filteredBtnHeight).append("\",");
            }
            if (StringUtils.hasText(filteredBtnRadius)) {
                jsonBuilder.append("\"btnRadius\":\"").append(filteredBtnRadius).append("\",");
            }
            if (jsonBuilder.length() > 1) {
                jsonBuilder.deleteCharAt(jsonBuilder.length() - 1); // 删除最后一个逗号
            }
            jsonBuilder.append("}");
            resourcePathVO.setRemark(jsonBuilder.toString());
        }
        
        ResourcePath resourcePath = new ResourcePath();
        BeanUtils.copyProperties(resourcePathVO, resourcePath);
        // 使用过滤后的值覆盖
        resourcePath.setTitle(filteredTitle);
        resourcePath.setIntroduction(filteredIntroduction);
        resourcePath.setUrl(filteredUrl);
        resourcePath.setCover(filteredCover);
        resourcePath.setClassify(filteredClassify);
        resourcePathMapper.insert(resourcePath);
        
        // 如果是收藏夹类型、本站信息类型或友链类型的资源，重新渲染相关页面
        try {
            if (CommonConst.RESOURCE_PATH_TYPE_FAVORITES.equals(resourcePathVO.getType())) {
                prerenderFacade.refreshFavoritesPage();
            } else if (CommonConst.RESOURCE_PATH_TYPE_SITE_INFO.equals(resourcePathVO.getType()) ||
                       CommonConst.RESOURCE_PATH_TYPE_FRIEND.equals(resourcePathVO.getType())) {
                prerenderFacade.refreshFriendsPage();
            }
        } catch (Exception e) {
            // 预渲染失败不影响主流程
        }

        // 联系方式/快捷入口/侧边栏背景变更后，失效侧边栏首屏聚合缓存
        // evictAsideBootstrap 内部已捕获异常，失败不影响主流程
        if (CommonConst.RESOURCE_PATH_TYPE_CONTACT.equals(resourcePathVO.getType()) ||
            CommonConst.RESOURCE_PATH_TYPE_QUICK_ENTRY.equals(resourcePathVO.getType()) ||
            CommonConst.RESOURCE_PATH_TYPE_ASIDE_BACKGROUND.equals(resourcePathVO.getType())) {
            cacheService.evictAsideBootstrap();
        }

        // 友链变更后，失效友人帐友链列表缓存
        if (CommonConst.RESOURCE_PATH_TYPE_FRIEND.equals(resourcePathVO.getType())) {
            cacheService.evictFriendList();
        }

        return PoetryResult.success();
    }

    /**
     * 删除
     */
    @GetMapping("/deleteResourcePath")
    @LoginCheck(0)
    public PoetryResult deleteResourcePath(@RequestParam("id") Integer id) {
        // 先获取资源信息以确定类型
        ResourcePath resourcePath = resourcePathMapper.selectById(id);
        
        resourcePathMapper.deleteById(id);

        // 如果是收藏夹类型、本站信息类型或友链类型的资源，重新渲染相关页面
        if (resourcePath != null) {
            try {
                if (CommonConst.RESOURCE_PATH_TYPE_FAVORITES.equals(resourcePath.getType())) {
                    prerenderFacade.refreshFavoritesPage();
                } else if (CommonConst.RESOURCE_PATH_TYPE_SITE_INFO.equals(resourcePath.getType()) ||
                           CommonConst.RESOURCE_PATH_TYPE_FRIEND.equals(resourcePath.getType())) {
                    prerenderFacade.refreshFriendsPage();
                }
            } catch (Exception e) {
                // 预渲染失败不影响主流程
            }

            // 联系方式/快捷入口/侧边栏背景删除后，失效侧边栏首屏聚合缓存
            if (CommonConst.RESOURCE_PATH_TYPE_CONTACT.equals(resourcePath.getType()) ||
                CommonConst.RESOURCE_PATH_TYPE_QUICK_ENTRY.equals(resourcePath.getType()) ||
                CommonConst.RESOURCE_PATH_TYPE_ASIDE_BACKGROUND.equals(resourcePath.getType())) {
                cacheService.evictAsideBootstrap();
            }

            // 友链删除后，失效友人帐友链列表缓存
            if (CommonConst.RESOURCE_PATH_TYPE_FRIEND.equals(resourcePath.getType())) {
                cacheService.evictFriendList();
            }
        }

        return PoetryResult.success();
    }

    /**
     * 更新
     */
    @PostMapping("/updateResourcePath")
    @LoginCheck(0)
    public PoetryResult updateResourcePath(@RequestBody ResourcePathVO resourcePathVO) {
        if (resourcePathVO.getId() == null) {
            return PoetryResult.fail("Id不能为空！");
        }
        
        // XSS过滤处理
        String filteredTitle = StringUtils.hasText(resourcePathVO.getTitle()) ? XssFilterUtil.clean(resourcePathVO.getTitle()) : null;
        String filteredIntroduction = StringUtils.hasText(resourcePathVO.getIntroduction()) ? XssFilterUtil.clean(resourcePathVO.getIntroduction()) : null;
        String filteredUrl = StringUtils.hasText(resourcePathVO.getUrl()) ? XssFilterUtil.clean(resourcePathVO.getUrl()) : null;
        String filteredCover = StringUtils.hasText(resourcePathVO.getCover()) ? XssFilterUtil.clean(resourcePathVO.getCover()) : null;
        String filteredClassify = StringUtils.hasText(resourcePathVO.getClassify()) ? XssFilterUtil.clean(resourcePathVO.getClassify()) : null;
        String filteredRemark = StringUtils.hasText(resourcePathVO.getRemark()) ? XssFilterUtil.clean(resourcePathVO.getRemark()) : null;
        String filteredExtraBackground = StringUtils.hasText(resourcePathVO.getExtraBackground()) ? XssFilterUtil.clean(resourcePathVO.getExtraBackground()) : null;
        String filteredBtnWidth = StringUtils.hasText(resourcePathVO.getBtnWidth()) ? XssFilterUtil.clean(resourcePathVO.getBtnWidth()) : null;
        String filteredBtnHeight = StringUtils.hasText(resourcePathVO.getBtnHeight()) ? XssFilterUtil.clean(resourcePathVO.getBtnHeight()) : null;
        String filteredBtnRadius = StringUtils.hasText(resourcePathVO.getBtnRadius()) ? XssFilterUtil.clean(resourcePathVO.getBtnRadius()) : null;
        
        // 侧边栏背景类型的特殊验证
        if (CommonConst.RESOURCE_PATH_TYPE_ASIDE_BACKGROUND.equals(resourcePathVO.getType())) {
            if (!StringUtils.hasText(filteredCover)) {
                return PoetryResult.fail("侧边栏背景图片/CSS代码不能为空或包含不安全内容！");
            }
        } else {
            if (!StringUtils.hasText(filteredTitle) || !StringUtils.hasText(resourcePathVO.getType())) {
                return PoetryResult.fail("标题和资源类型不能为空或包含不安全内容！");
            }
        }
        if (CommonConst.RESOURCE_PATH_TYPE_LOVE_PHOTO.equals(resourcePathVO.getType())) {
            resourcePathVO.setRemark(PoetryUtil.getAdminUser().getId().toString());
        }
        if (CommonConst.RESOURCE_PATH_TYPE_SITE_INFO.equals(resourcePathVO.getType())) {
            resourcePathVO.setUrl(null);
        }
        
        // 侧边栏背景：自动设置标题，并将额外背景层存储到remark
        if (CommonConst.RESOURCE_PATH_TYPE_ASIDE_BACKGROUND.equals(resourcePathVO.getType())) {
            resourcePathVO.setTitle("侧边栏背景");
            if (StringUtils.hasText(filteredExtraBackground)) {
                // 转义双引号和反斜杠
                String escapedExtra = filteredExtraBackground
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"");
                resourcePathVO.setRemark("{\"extraBackground\":\"" + escapedExtra + "\"}");
            } else {
                resourcePathVO.setRemark(null); // 没有额外背景则不设置remark
            }
        }
        
        // 快捷入口和联系方式：将样式转换为JSON存储到remark字段
        if (CommonConst.RESOURCE_PATH_TYPE_QUICK_ENTRY.equals(resourcePathVO.getType()) || 
            CommonConst.RESOURCE_PATH_TYPE_CONTACT.equals(resourcePathVO.getType())) {
            StringBuilder jsonBuilder = new StringBuilder("{");
            if (StringUtils.hasText(filteredBtnWidth)) {
                jsonBuilder.append("\"btnWidth\":\"").append(filteredBtnWidth).append("\",");
            }
            if (StringUtils.hasText(filteredBtnHeight)) {
                jsonBuilder.append("\"btnHeight\":\"").append(filteredBtnHeight).append("\",");
            }
            if (StringUtils.hasText(filteredBtnRadius)) {
                jsonBuilder.append("\"btnRadius\":\"").append(filteredBtnRadius).append("\",");
            }
            if (jsonBuilder.length() > 1) {
                jsonBuilder.deleteCharAt(jsonBuilder.length() - 1); // 删除最后一个逗号
            }
            jsonBuilder.append("}");
            resourcePathVO.setRemark(jsonBuilder.toString());
        }
        
        ResourcePath resourcePath = new ResourcePath();
        BeanUtils.copyProperties(resourcePathVO, resourcePath);
        // 使用过滤后的值覆盖
        resourcePath.setTitle(filteredTitle);
        resourcePath.setIntroduction(filteredIntroduction);
        resourcePath.setUrl(filteredUrl);
        resourcePath.setCover(filteredCover);
        resourcePath.setClassify(filteredClassify);
        resourcePathMapper.updateById(resourcePath);
        
        // 如果是收藏夹类型、本站信息类型或友链类型的资源，重新渲染相关页面
        try {
            if (CommonConst.RESOURCE_PATH_TYPE_FAVORITES.equals(resourcePathVO.getType())) {
                prerenderFacade.refreshFavoritesPage();
            } else if (CommonConst.RESOURCE_PATH_TYPE_SITE_INFO.equals(resourcePathVO.getType()) ||
                       CommonConst.RESOURCE_PATH_TYPE_FRIEND.equals(resourcePathVO.getType())) {
                prerenderFacade.refreshFriendsPage();
            }
        } catch (Exception e) {
            // 预渲染失败不影响主流程
        }

        // 联系方式/快捷入口/侧边栏背景更新后，失效侧边栏首屏聚合缓存
        if (CommonConst.RESOURCE_PATH_TYPE_CONTACT.equals(resourcePathVO.getType()) ||
            CommonConst.RESOURCE_PATH_TYPE_QUICK_ENTRY.equals(resourcePathVO.getType()) ||
            CommonConst.RESOURCE_PATH_TYPE_ASIDE_BACKGROUND.equals(resourcePathVO.getType())) {
            cacheService.evictAsideBootstrap();
        }

        // 友链更新后，失效友人帐友链列表缓存
        if (CommonConst.RESOURCE_PATH_TYPE_FRIEND.equals(resourcePathVO.getType())) {
            cacheService.evictFriendList();
        }

        return PoetryResult.success();
    }

    /**
     * 获取本站信息
     */
    @GetMapping("/getSiteInfo")
    public PoetryResult<ResourcePathVO> getSiteInfo() {
        LambdaQueryChainWrapper<ResourcePath> wrapper = new LambdaQueryChainWrapper<>(resourcePathMapper);
        ResourcePath resourcePath = wrapper.eq(ResourcePath::getType, CommonConst.RESOURCE_PATH_TYPE_SITE_INFO)
                .eq(ResourcePath::getStatus, Boolean.TRUE)
                .one();
        
        if (resourcePath != null) {
            ResourcePathVO resourcePathVO = new ResourcePathVO();
            BeanUtils.copyProperties(resourcePath, resourcePathVO);
            resourcePathVO.setUrl(mailUtil.getSiteUrl());
            return PoetryResult.success(resourcePathVO);
        }
        
        // 如果没有配置本站信息，返回默认值
        ResourcePathVO defaultSiteInfo = new ResourcePathVO();
        defaultSiteInfo.setTitle("POETIZE");
        defaultSiteInfo.setUrl(mailUtil.getSiteUrl());
        defaultSiteInfo.setCover("https://s1.ax1x.com/2022/11/10/z9VlHs.png");
        defaultSiteInfo.setIntroduction("这是一个 Vue2 Vue3 与 SpringBoot 结合的产物～");
        
        return PoetryResult.success(defaultSiteInfo);
    }

    /**
     * 查询资源
     */
    @PostMapping("/listResourcePath")
    public PoetryResult<Page> listResourcePath(@RequestBody BaseRequestVO baseRequestVO) {
        LambdaQueryChainWrapper<ResourcePath> wrapper = new LambdaQueryChainWrapper<>(resourcePathMapper);
        wrapper.like(StringUtils.hasText(baseRequestVO.getSearchKey()), ResourcePath::getTitle, baseRequestVO.getSearchKey());
        if (StringUtils.hasText(baseRequestVO.getResourceType())) {
            wrapper.eq(ResourcePath::getType, baseRequestVO.getResourceType());
        } else if (!CollectionUtils.isEmpty(baseRequestVO.getResourceTypes())) {
            wrapper.in(ResourcePath::getType, baseRequestVO.getResourceTypes());
        }
        if (StringUtils.hasText(baseRequestVO.getClassify())) {
            wrapper.eq(ResourcePath::getClassify, baseRequestVO.getClassify());
        }
        Integer userId = PoetryUtil.getUserId();
        if (!PoetryUtil.getAdminUser().getId().equals(userId)) {
            wrapper.eq(ResourcePath::getStatus, Boolean.TRUE);
        } else {
            wrapper.eq(baseRequestVO.getStatus() != null, ResourcePath::getStatus, baseRequestVO.getStatus());
        }

        OrderItem orderItem = new OrderItem();
        orderItem.setColumn(StringUtils.hasText(baseRequestVO.getOrder()) ? StrUtil.toUnderlineCase(baseRequestVO.getOrder()) : "create_time");
        orderItem.setAsc(!baseRequestVO.isDesc());
        List<OrderItem> orderItemList = new ArrayList<>();
        orderItemList.add(orderItem);
        
        // 创建Page对象并设置排序
        Page<ResourcePath> page = new Page<>(baseRequestVO.getCurrent(), baseRequestVO.getSize());
        page.setOrders(orderItemList);
        
        // 执行分页查询
        Page<ResourcePath> resultPage = wrapper.page(page);
        
        List<ResourcePath> resourcePaths = resultPage.getRecords();
        if (!CollectionUtils.isEmpty(resourcePaths)) {
            List<ResourcePathVO> resourcePathVOs = resourcePaths.stream()
                .map(this::toResourcePathVO)
                .collect(Collectors.toList());
            baseRequestVO.setRecords(resourcePathVOs);
            baseRequestVO.setTotal(resultPage.getTotal());
        }
        return PoetryResult.success(baseRequestVO);
    }

    /**
     * ResourcePath -> ResourcePathVO，并对快捷入口/联系方式/侧边栏背景解析 remark 中的样式 JSON。
     */
    private ResourcePathVO toResourcePathVO(ResourcePath rp) {
        ResourcePathVO vo = new ResourcePathVO();
        BeanUtils.copyProperties(rp, vo);

        // 快捷入口、联系方式、侧边栏背景：解析remark中的JSON，设置样式字段
        if ((CommonConst.RESOURCE_PATH_TYPE_QUICK_ENTRY.equals(rp.getType()) ||
             CommonConst.RESOURCE_PATH_TYPE_CONTACT.equals(rp.getType()) ||
             CommonConst.RESOURCE_PATH_TYPE_ASIDE_BACKGROUND.equals(rp.getType())) && StringUtils.hasText(rp.getRemark())) {
            String remark = rp.getRemark().trim();
            if (remark.startsWith("{") && remark.endsWith("}")) {
                // 简单的JSON解析（避免引入额外依赖）
                remark = remark.substring(1, remark.length() - 1); // 去掉 {}
                String[] pairs = remark.split(",");
                for (String pair : pairs) {
                    String[] keyValue = pair.split(":", 2);
                    if (keyValue.length == 2) {
                        String key = keyValue[0].trim().replace("\"", "");
                        String value = keyValue[1].trim().replace("\"", "");
                        if ("btnWidth".equals(key)) {
                            vo.setBtnWidth(value);
                        } else if ("btnHeight".equals(key)) {
                            vo.setBtnHeight(value);
                        } else if ("btnRadius".equals(key)) {
                            vo.setBtnRadius(value);
                        } else if ("extraBackground".equals(key)) {
                            vo.setExtraBackground(value);
                        }
                    }
                }
            }
        }

        return vo;
    }

    /**
     * 前台侧边栏首屏聚合：一次返回联系方式、快捷入口、侧边栏背景。
     * 替代 myAside.vue 中对 /webInfo/listResourcePath 的 3 次独立请求。
     * <p>响应设 Cache-Control 让 CDN 边缘缓存 10 秒，stale-while-revalidate 允许 5 分钟内返回旧值同时异步刷新；
     * 服务端走 Redis 永久缓存，后台改 resource_path 时由 save/update/delete 主动 evict，10 秒内 CDN 同步生效。
     */
    @GetMapping("/asideBootstrap")
    public PoetryResult<Map<String, Object>> asideBootstrap(jakarta.servlet.http.HttpServletResponse response) {
        // 与 /webInfo/bootstrap 策略一致：CDN 边缘缓存 10 秒，5 分钟内可返回旧值同时异步刷新
        response.setHeader("Cache-Control",
                "public, max-age=0, s-maxage=10, stale-while-revalidate=300, must-revalidate");
        return PoetryResult.success(loadAsideBootstrapData());
    }

    /**
     * 加载侧边栏首屏聚合数据（读缓存→未命中查 DB→回填缓存）。
     * 供 asideBootstrap 端点和启动预热 Runner 共用，保证首个用户请求不承担 DB 查询延迟。
     */
    public Map<String, Object> loadAsideBootstrapData() {
        // 优先走 Redis 缓存（首屏高频读，后台改配置后由 save/update/delete 主动 evict）
        Map<String, Object> cached = cacheService.getCachedAsideBootstrap();
        if (cached != null) {
            return cached;
        }

        Map<String, Object> result = new LinkedHashMap<>();

        try {
            List<ResourcePath> contacts = new LambdaQueryChainWrapper<>(resourcePathMapper)
                .eq(ResourcePath::getType, CommonConst.RESOURCE_PATH_TYPE_CONTACT)
                .eq(ResourcePath::getStatus, Boolean.TRUE)
                .orderByAsc(ResourcePath::getCreateTime)
                .list();
            result.put("contactList", contacts.stream().map(this::toResourcePathVO).collect(Collectors.toList()));
        } catch (Exception e) {
            result.put("contactList", Collections.emptyList());
        }

        try {
            List<ResourcePath> quickEntries = new LambdaQueryChainWrapper<>(resourcePathMapper)
                .eq(ResourcePath::getType, CommonConst.RESOURCE_PATH_TYPE_QUICK_ENTRY)
                .eq(ResourcePath::getStatus, Boolean.TRUE)
                .orderByAsc(ResourcePath::getCreateTime)
                .list();
            result.put("quickEntryList", quickEntries.stream().map(this::toResourcePathVO).collect(Collectors.toList()));
        } catch (Exception e) {
            result.put("quickEntryList", Collections.emptyList());
        }

        try {
            List<ResourcePath> backgrounds = new LambdaQueryChainWrapper<>(resourcePathMapper)
                .eq(ResourcePath::getType, CommonConst.RESOURCE_PATH_TYPE_ASIDE_BACKGROUND)
                .eq(ResourcePath::getStatus, Boolean.TRUE)
                .orderByDesc(ResourcePath::getCreateTime)
                .last("LIMIT 1")
                .list();
            result.put("asideBackground", backgrounds.isEmpty() ? null : toResourcePathVO(backgrounds.get(0)));
        } catch (Exception e) {
            result.put("asideBackground", null);
        }

        // 写回 Redis 永久缓存（仅在缓存缺失时回源并写回，避免每次请求都打 DB）
        cacheService.cacheAsideBootstrap(result);

        return result;
    }
}
