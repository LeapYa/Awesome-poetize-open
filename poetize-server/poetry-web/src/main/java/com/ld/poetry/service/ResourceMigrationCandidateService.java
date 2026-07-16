package com.ld.poetry.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.controller.dto.ResourceMigrationPreview;
import com.ld.poetry.controller.dto.ResourceMigrationRequest;
import com.ld.poetry.dao.ResourceMapper;
import com.ld.poetry.entity.Resource;
import com.ld.poetry.entity.ResourceLocation;
import com.ld.poetry.enums.ResourceContentState;
import com.ld.poetry.enums.ResourceLocationStatus;
import com.ld.poetry.utils.storage.FileStorageService;
import com.ld.poetry.utils.storage.StorageCapability;
import com.ld.poetry.utils.storage.StoreEnum;
import com.ld.poetry.utils.storage.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ResourceMigrationCandidateService {

    private static final int MAX_SELECTED_COUNT = 500;
    private static final int MAX_FILTER_COUNT = 10_000;
    private static final int PREVIEW_ITEM_LIMIT = 200;

    private final ResourceService resourceService;
    private final ResourceMapper resourceMapper;
    private final ResourceAvailabilityService resourceAvailabilityService;
    private final ResourceLocationService resourceLocationService;
    private final FileStorageService fileStorageService;

    public ResourceMigrationPreview preview(ResourceMigrationRequest request) {
        StorageCapability capability = resolveTarget(request).getCapability();
        List<ResourceMigrationCandidate> candidates = resolveCandidates(request, capability, false);
        int eligibleCount = (int) candidates.stream().filter(ResourceMigrationCandidate::eligible).count();
        long eligibleBytes = candidates.stream()
                .filter(ResourceMigrationCandidate::eligible)
                .map(ResourceMigrationCandidate::sourceLocation)
                .filter(java.util.Objects::nonNull)
                .map(ResourceLocation::getSize)
                .filter(java.util.Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
        List<ResourceMigrationPreview.Item> items = candidates.stream()
                .limit(PREVIEW_ITEM_LIMIT)
                .map(this::toPreviewItem)
                .toList();
        return new ResourceMigrationPreview(
                capability,
                candidates.size(),
                eligibleCount,
                candidates.size() - eligibleCount,
                eligibleBytes,
                items,
                candidates.size() > PREVIEW_ITEM_LIMIT
        );
    }

    /**
     * 创建任务时登记尚未接管的历史资源，确保条目冻结的是物理副本而不是易变化的 resource.path。
     */
    public List<ResourceMigrationCandidate> resolveCandidates(ResourceMigrationRequest request) {
        StorageCapability capability = resolveTarget(request).getCapability();
        return resolveCandidates(request, capability, true);
    }

    public StoreService resolveTarget(ResourceMigrationRequest request) {
        if (request == null || !StringUtils.hasText(request.targetStoreType())) {
            throw new IllegalArgumentException("请选择目标存储");
        }
        StoreService storeService = fileStorageService.getFileStorageByStoreType(request.targetStoreType());
        StorageCapability capability = storeService.getCapability();
        if (!capability.enabled()
                || !capability.uploadSupported()
                || !capability.readSupported()
                || !capability.verifySupported()
                || !storeService.supportsDeterministicWrite()) {
            throw new IllegalArgumentException("目标存储不支持确定性写入、目标探测与完整回读校验");
        }
        return storeService;
    }

    private List<ResourceMigrationCandidate> resolveCandidates(ResourceMigrationRequest request,
                                                                 StorageCapability capability,
                                                                 boolean ensureManaged) {
        if (request == null) {
            throw new IllegalArgumentException("迁移请求不能为空");
        }
        List<Resource> resources = "FILTER".equalsIgnoreCase(request.scopeType())
                ? resolveFilterResources(request.resourceType())
                : resolveSelectedResources(request.targets());
        return resources.stream()
                .map(resource -> inspect(resource, capability, ensureManaged))
                .toList();
    }

    private List<Resource> resolveSelectedResources(List<ResourceMigrationRequest.Target> targets) {
        if (CollectionUtils.isEmpty(targets)) {
            throw new IllegalArgumentException("请选择要迁移的资源");
        }
        if (targets.size() > MAX_SELECTED_COUNT) {
            throw new IllegalArgumentException("单次最多选择 " + MAX_SELECTED_COUNT + " 个资源");
        }

        Map<Integer, ResourceMigrationRequest.Target> uniqueTargets = new LinkedHashMap<>();
        for (ResourceMigrationRequest.Target target : targets) {
            if (target == null || target.resourceId() == null || !StringUtils.hasText(target.expectedPath())) {
                throw new IllegalArgumentException("资源ID和期望路径不能为空");
            }
            uniqueTargets.putIfAbsent(target.resourceId(), target);
        }

        Map<Integer, Resource> resourcesById = resourceService.listByIds(uniqueTargets.keySet()).stream()
                .collect(Collectors.toMap(Resource::getId, resource -> resource));
        List<Resource> resources = new ArrayList<>(uniqueTargets.size());
        for (ResourceMigrationRequest.Target target : uniqueTargets.values()) {
            Resource resource = resourcesById.get(target.resourceId());
            if (resource == null) {
                throw new IllegalArgumentException("资源不存在，请刷新列表后重试：" + target.resourceId());
            }
            if (!target.expectedPath().equals(resource.getPath())) {
                throw new IllegalArgumentException("资源路径已变化，请刷新列表后重试：" + target.resourceId());
            }
            resources.add(resource);
        }
        return resources;
    }

    private List<Resource> resolveFilterResources(String resourceType) {
        Page<Resource> page = new Page<>(1, MAX_FILTER_COUNT + 1L);
        List<Resource> resources;
        if (CommonConst.PATH_TYPE_ORPHAN_RESOURCE.equals(resourceType)) {
            resources = resourceMapper.selectOrphanResources(
                    page,
                    List.of(CommonConst.PATH_TYPE_ASSETS),
                    "create_time",
                    false
            ).getRecords();
        } else if (CommonConst.PATH_TYPE_INVALID_RESOURCE.equals(resourceType)) {
            Page<Resource> cached = resourceAvailabilityService.listInvalidResourcesFromCache(
                    page, "createTime", false
            );
            resources = (cached != null
                    ? cached
                    : resourceAvailabilityService.listInvalidResources(page, "createTime", false)).getRecords();
        } else {
            var query = resourceService.lambdaQuery();
            if (StringUtils.hasText(resourceType)) {
                query.eq(Resource::getType, resourceType);
            }
            resources = query.orderByDesc(Resource::getCreateTime)
                    .last("limit " + (MAX_FILTER_COUNT + 1))
                    .list();
        }

        List<Resource> uniqueResources = resources.stream()
                .collect(Collectors.toMap(
                        Resource::getId,
                        resource -> resource,
                        (left, right) -> left,
                        LinkedHashMap::new
                ))
                .values()
                .stream()
                .toList();
        if (uniqueResources.size() > MAX_FILTER_COUNT) {
            throw new IllegalArgumentException("当前筛选结果超过 " + MAX_FILTER_COUNT + " 条，请缩小范围后迁移");
        }
        return uniqueResources;
    }

    private ResourceMigrationCandidate inspect(Resource originalResource,
                                                 StorageCapability targetCapability,
                                                 boolean ensureManaged) {
        Resource resource = originalResource;
        if (!ResourceContentState.isActive(resource.getContentState())) {
            return new ResourceMigrationCandidate(
                    resource,
                    null,
                    false,
                    "资源存在未完成的内容替换，请先恢复或核验"
            );
        }
        ResourceLocation sourceLocation;
        try {
            if (ensureManaged) {
                resource = resourceLocationService.ensureManaged(originalResource.getId());
                sourceLocation = resourceLocationService.requireActiveLocation(resource);
            } else {
                sourceLocation = resolvePreviewLocation(resource);
            }
        } catch (RuntimeException e) {
            return new ResourceMigrationCandidate(resource, null, false, e.getMessage());
        }

        if (!StringUtils.hasText(resource.getPublicId())
                || !resourceLocationService.stablePath(resource.getPublicId()).equals(resource.getPath())) {
            return new ResourceMigrationCandidate(
                    resource,
                    sourceLocation,
                    false,
                    "资源尚未归一化为稳定地址，请先执行历史资源接管"
            );
        }
        if (targetCapability.storeType().equals(sourceLocation.getStoreType())) {
            return new ResourceMigrationCandidate(resource, sourceLocation, false, "资源已处于目标存储");
        }
        if (!StringUtils.hasText(sourceLocation.getAccessPath())) {
            return new ResourceMigrationCandidate(resource, sourceLocation, false, "源物理副本访问地址为空");
        }

        StorageCapability sourceCapability;
        try {
            sourceCapability = fileStorageService
                    .getFileStorageByStoreType(sourceLocation.getStoreType())
                    .getCapability();
        } catch (RuntimeException e) {
            return new ResourceMigrationCandidate(resource, sourceLocation, false, "源存储适配器不可用");
        }
        if (!sourceCapability.enabled() || !sourceCapability.readSupported()) {
            return new ResourceMigrationCandidate(resource, sourceLocation, false, "源存储不支持完整读取原始文件");
        }

        String mimeType = resolveMimeType(resource, sourceLocation);
        long size = resolveSize(resource, sourceLocation);
        if (!targetCapability.supports(mimeType, size)) {
            String reason = targetCapability.maxFileSize() > 0 && size > targetCapability.maxFileSize()
                    ? "文件超过目标存储大小限制"
                    : "目标存储不支持该文件类型：" + mimeType;
            return new ResourceMigrationCandidate(resource, sourceLocation, false, reason);
        }
        return new ResourceMigrationCandidate(resource, sourceLocation, true, "");
    }

    private ResourceLocation resolvePreviewLocation(Resource resource) {
        if (resource.getActiveLocationId() != null) {
            return resourceLocationService.requireActiveLocation(resource);
        }
        if (!StringUtils.hasText(resource.getPath()) || resource.getPath().startsWith("/media/")) {
            throw new IllegalStateException("资源尚未登记可读取的活动物理副本");
        }

        ResourceLocation location = new ResourceLocation();
        location.setResourceId(resource.getId());
        location.setStoreType(normalizeStoreType(resource.getStoreType()));
        location.setStorageKey(resource.getStorageKey());
        location.setAccessPath(resource.getPath());
        location.setContentHash(resource.getResourceHash());
        location.setSize(resource.getSize() == null ? null : resource.getSize().longValue());
        location.setMimeType(resource.getMimeType());
        location.setStatus(ResourceLocationStatus.ACTIVE.name());
        return location;
    }

    private long resolveSize(Resource resource, ResourceLocation sourceLocation) {
        if (sourceLocation.getSize() != null) {
            return sourceLocation.getSize();
        }
        return resource.getSize() == null ? 0 : resource.getSize().longValue();
    }

    private String resolveMimeType(Resource resource, ResourceLocation sourceLocation) {
        if (StringUtils.hasText(sourceLocation.getMimeType())) {
            return sourceLocation.getMimeType().toLowerCase();
        }
        if (StringUtils.hasText(resource.getMimeType())) {
            return resource.getMimeType().toLowerCase();
        }
        String path = String.valueOf(sourceLocation.getAccessPath()).toLowerCase();
        int queryIndex = path.indexOf('?');
        if (queryIndex >= 0) {
            path = path.substring(0, queryIndex);
        }
        if (path.matches(".*\\.(png|jpe?g|gif|webp|bmp|avif|ico|svg)$")) {
            return "image/unknown";
        }
        if (path.matches(".*\\.(mp4|webm|mov|m4v|ogg|ogv)$")) {
            return "video/unknown";
        }
        if (path.matches(".*\\.(woff2?|ttf|otf|eot)$")) {
            return "font/unknown";
        }
        return "application/octet-stream";
    }

    private ResourceMigrationPreview.Item toPreviewItem(ResourceMigrationCandidate candidate) {
        Resource resource = candidate.resource();
        ResourceLocation sourceLocation = candidate.sourceLocation();
        return new ResourceMigrationPreview.Item(
                resource.getId(),
                resource.getPath(),
                resource.getOriginalName(),
                sourceLocation == null ? resource.getMimeType() : resolveMimeType(resource, sourceLocation),
                sourceLocation == null ? null : sourceLocation.getSize(),
                candidate.eligible(),
                candidate.reason()
        );
    }

    private String normalizeStoreType(String storeType) {
        return StringUtils.hasText(storeType) ? storeType : StoreEnum.LOCAL.getCode();
    }
}