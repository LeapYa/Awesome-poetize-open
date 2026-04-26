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
import java.util.List;
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
            List<ResourcePathVO> resourcePathVOs = resourcePaths.stream().map(rp -> {
                ResourcePathVO resourcePathVO = new ResourcePathVO();
                BeanUtils.copyProperties(rp, resourcePathVO);
                
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
                                    resourcePathVO.setBtnWidth(value);
                                } else if ("btnHeight".equals(key)) {
                                    resourcePathVO.setBtnHeight(value);
                                } else if ("btnRadius".equals(key)) {
                                    resourcePathVO.setBtnRadius(value);
                                } else if ("extraBackground".equals(key)) {
                                    resourcePathVO.setExtraBackground(value);
                                }
                            }
                        }
                    }
                }
                
                return resourcePathVO;
            }).collect(Collectors.toList());
            baseRequestVO.setRecords(resourcePathVOs);
            baseRequestVO.setTotal(resultPage.getTotal());
        }
        return PoetryResult.success(baseRequestVO);
    }
}
