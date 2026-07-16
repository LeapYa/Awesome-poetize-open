package com.ld.poetry.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ld.poetry.controller.dto.ResourceDetailView;
import com.ld.poetry.dao.ResourceAliasMapper;
import com.ld.poetry.dao.ResourceLocationMapper;
import com.ld.poetry.dao.ResourceMapper;
import com.ld.poetry.entity.Resource;
import com.ld.poetry.entity.ResourceAlias;
import com.ld.poetry.entity.ResourceLocation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 资源详情聚合服务：为管理端资源详情对话框提供逻辑资源、全部物理副本、活动别名与引用计数的统一视图。
 *
 * <p>引用计数口径与 {@link ResourceDeletionStateService#countReferences} 一致：
 * 稳定地址（{@code /media/{publicId}}）加上全部状态为启用的活动别名，对每个身份分别调用
 * {@link ResourceReferenceService#countReferences(String)} 后求和，避免重复 URL 重复计数。
 */
@Service
@RequiredArgsConstructor
public class ResourceDetailService {

    private final ResourceMapper resourceMapper;
    private final ResourceLocationMapper resourceLocationMapper;
    private final ResourceAliasMapper resourceAliasMapper;
    private final ResourceReferenceService referenceService;

    public ResourceDetailView loadDetail(Integer resourceId) {
        if (resourceId == null) {
            throw new IllegalArgumentException("资源 ID 不能为空");
        }
        Resource resource = resourceMapper.selectById(resourceId);
        if (resource == null) {
            throw new IllegalArgumentException("资源不存在：" + resourceId);
        }
        List<ResourceLocation> locations = resourceLocationMapper.findByResourceId(resourceId);
        List<ResourceAlias> aliases = resourceAliasMapper.selectList(
                Wrappers.<ResourceAlias>lambdaQuery()
                        .eq(ResourceAlias::getResourceId, resourceId)
                        .orderByAsc(ResourceAlias::getId)
        );
        int referenceCount = countReferences(resource, aliases);
        return new ResourceDetailView(resource, locations, aliases, referenceCount, resource.getPath());
    }

    /**
     * 稳定地址与全部活动别名的引用计数总和。重复 URL 通过 {@link LinkedHashSet} 去重，
     * 避免同一身份被多次统计。
     */
    private int countReferences(Resource resource, List<ResourceAlias> aliases) {
        Set<String> identities = new LinkedHashSet<>();
        if (StringUtils.hasText(resource.getPath())) {
            identities.add(resource.getPath());
        }
        aliases.stream()
                .filter(ResourceAlias::getStatus)
                .map(ResourceAlias::getAliasUrl)
                .filter(StringUtils::hasText)
                .forEach(identities::add);
        return identities.stream()
                .filter(StringUtils::hasText)
                .mapToInt(referenceService::countReferences)
                .sum();
    }
}
