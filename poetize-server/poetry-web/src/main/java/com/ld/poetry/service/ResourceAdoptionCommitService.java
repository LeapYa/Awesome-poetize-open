package com.ld.poetry.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ld.poetry.dao.ResourceAdoptionItemMapper;
import com.ld.poetry.dao.ResourceAdoptionTaskMapper;
import com.ld.poetry.dao.ResourceAliasMapper;
import com.ld.poetry.dao.ResourceLocationMapper;
import com.ld.poetry.dao.ResourceMapper;
import com.ld.poetry.entity.Resource;
import com.ld.poetry.entity.ResourceAdoptionItem;
import com.ld.poetry.entity.ResourceAdoptionTask;
import com.ld.poetry.entity.ResourceAlias;
import com.ld.poetry.entity.ResourceLocation;
import com.ld.poetry.enums.ResourceAdoptionItemStatus;
import com.ld.poetry.enums.ResourceAdoptionTaskStatus;
import com.ld.poetry.enums.ResourceContentState;
import com.ld.poetry.enums.ResourceLocationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ConcurrentModificationException;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ResourceAdoptionCommitService {

    private final ResourceMapper resourceMapper;
    private final ResourceLocationMapper locationMapper;
    private final ResourceAliasMapper aliasMapper;
    private final ResourceAdoptionTaskMapper taskMapper;
    private final ResourceAdoptionItemMapper itemMapper;
    private final ResourceReferenceService referenceService;
    private final ResourceLocationService resourceLocationService;

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.SERIALIZABLE)
    public CommitResult commit(VerifiedAdoption adoption) {
        validate(adoption);
        ResourceAdoptionItem discoveredItem = itemMapper.selectById(adoption.itemId());
        if (discoveredItem == null || !adoption.sourceUrl().equals(discoveredItem.getSourceUrl())) {
            throw new ConcurrentModificationException("接管条目不存在或来源URL已变化");
        }
        ResourceAdoptionTask task = taskMapper.findByTaskIdForUpdate(discoveredItem.getTaskId());
        if (task == null || !ResourceAdoptionTaskStatus.RUNNING.name().equals(task.getStatus())) {
            throw new ConcurrentModificationException("接管任务已取消或不再运行");
        }
        ResourceAdoptionItem item = itemMapper.selectByIdForUpdate(adoption.itemId());
        if (item == null
                || !task.getTaskId().equals(item.getTaskId())
                || !adoption.sourceUrl().equals(item.getSourceUrl())) {
            throw new ConcurrentModificationException("接管条目不存在或来源URL已变化");
        }
        if (!ResourceAdoptionItemStatus.READING.name().equals(item.getStatus())) {
            throw new ConcurrentModificationException("接管条目状态已变化");
        }

        int currentReferences = referenceService.countReferences(adoption.sourceUrl());
        if (currentReferences == 0) {
            markSkipped(item, "历史资源引用已不存在");
            return CommitResult.skippedResult();
        }
        if (item.getReferenceCount() == null || currentReferences != item.getReferenceCount()) {
            throw new ConcurrentModificationException("历史资源引用数量在完整回读期间发生变化，请重新预检");
        }

        ResourceAlias sourceAlias = aliasMapper.findActiveByAliasUrlForUpdate(adoption.sourceUrl());
        ResourceAlias accessAlias = adoption.sourceUrl().equals(adoption.accessPath())
                ? sourceAlias
                : aliasMapper.findActiveByAliasUrlForUpdate(adoption.accessPath());
        ResourceLocation sourceLocation = locationMapper.findByStoreAndAccessPathForUpdate(
                adoption.storeType(),
                adoption.accessPath()
        );
        Resource sourcePathResource = resourceMapper.findByPathForUpdate(adoption.sourceUrl());
        Resource accessPathResource = adoption.sourceUrl().equals(adoption.accessPath())
                ? sourcePathResource
                : resourceMapper.findByPathForUpdate(adoption.accessPath());

        Integer existingResourceId = uniqueResourceId(
                sourceAlias == null ? null : sourceAlias.getResourceId(),
                accessAlias == null ? null : accessAlias.getResourceId(),
                sourceLocation == null ? null : sourceLocation.getResourceId(),
                sourcePathResource == null ? null : sourcePathResource.getId(),
                accessPathResource == null ? null : accessPathResource.getId()
        );
        boolean createdResource = existingResourceId == null;
        Resource resource = createdResource
                ? createResource(adoption)
                : requireCompatibleResource(existingResourceId, adoption);

        if (sourceLocation != null && !resource.getId().equals(sourceLocation.getResourceId())) {
            throw new IllegalStateException("可信物理地址已属于其他逻辑资源");
        }
        Long previousActiveId = resource.getActiveLocationId();
        ResourceLocation currentActive = previousActiveId == null
                ? null
                : locationMapper.selectByIdForUpdate(previousActiveId);
        if (currentActive != null
                && (!resource.getId().equals(currentActive.getResourceId())
                || !ResourceLocationStatus.ACTIVE.name().equals(currentActive.getStatus()))) {
            throw new ConcurrentModificationException("原活动物理副本已变化");
        }

        boolean sourceBecomesActive = previousActiveId == null
                || (sourceLocation != null && previousActiveId.equals(sourceLocation.getId()));
        if (!sourceBecomesActive) {
            if (!hasVerifiedHash(resource)
                    || !hashEquals(resource.getResourceHash(), adoption.contentHash())
                    || currentActive == null
                    || currentActive.getVerifiedAt() == null
                    || !isSha256(currentActive.getContentHash())
                    || !hashEquals(currentActive.getContentHash(), adoption.contentHash())
                    || !java.util.Objects.equals(currentActive.getSize(), adoption.size())) {
                throw new IllegalStateException("旧别名来源无法证明当前活动副本内容一致");
            }
        }

        ResourceLocationStatus sourceStatus = sourceBecomesActive
                ? ResourceLocationStatus.ACTIVE
                : ResourceLocationStatus.RETAINED;
        ResourceLocation verifiedLocation = sourceLocation == null
                ? createLocation(resource.getId(), adoption, sourceStatus)
                : verifyAndRefreshLocation(sourceLocation, adoption, sourceStatus);
        ResourceLocation activeLocation = sourceBecomesActive ? verifiedLocation : currentActive;
        if (activeLocation == null) {
            throw new IllegalStateException("接管后活动物理副本不存在");
        }

        boolean baselined = createdResource || !hasVerifiedHash(resource);
        String previousPath = resource.getPath();
        String publicId = StringUtils.hasText(resource.getPublicId())
                ? resource.getPublicId()
                : UUID.randomUUID().toString().replace("-", "").toLowerCase(Locale.ROOT);
        String stablePath = resourceLocationService.stablePath(publicId);
        int nextVersion = normalizeVersion(resource.getLocationVersion());
        LocalDateTime activeVerifiedAt = sourceBecomesActive
                ? adoption.verifiedAt()
                : activeLocation.getVerifiedAt();

        var update = Wrappers.<Resource>lambdaUpdate()
                .eq(Resource::getId, resource.getId())
                .eq(Resource::getLocationVersion, normalizeVersion(resource.getLocationVersion()))
                .eq(Resource::getContentState, ResourceContentState.ACTIVE.name())
                .set(Resource::getPublicId, publicId)
                .set(Resource::getPath, stablePath)
                .set(Resource::getActiveLocationId, activeLocation.getId())
                .set(Resource::getLocationVersion, nextVersion)
                .set(Resource::getStoreType, activeLocation.getStoreType())
                .set(Resource::getStorageKey, activeLocation.getStorageKey())
                .set(Resource::getResourceHash, adoption.contentHash())
                .set(Resource::getHashVerifiedAt, activeVerifiedAt)
                .set(Resource::getSize, Math.toIntExact(adoption.size()))
                .set(Resource::getMimeType, adoption.mimeType())
                .set(Resource::getStatus, true);
        if (baselined) {
            update.set(Resource::getHashSource, "LEGACY_ADOPTION");
        }
        if (!StringUtils.hasText(resource.getOriginalName())) {
            update.set(Resource::getOriginalName, adoption.originalName());
        }
        if (resourceMapper.update(null, update) != 1) {
            throw new ConcurrentModificationException("逻辑资源在接管提交期间发生变化");
        }

        registerAlias(resource.getId(), adoption.sourceUrl());
        if (!adoption.sourceUrl().equals(adoption.accessPath())) {
            registerAlias(resource.getId(), adoption.accessPath());
        }
        if (StringUtils.hasText(previousPath) && !previousPath.startsWith("/media/")) {
            registerAlias(resource.getId(), previousPath);
        }

        ResourceReferenceService.ReplacementResult replacement =
                referenceService.replaceReferences(adoption.sourceUrl(), stablePath);
        if (referenceService.countReferences(adoption.sourceUrl()) != 0) {
            throw new ConcurrentModificationException("历史资源仍存在未归一化的精确引用");
        }

        int itemUpdated = itemMapper.update(
                null,
                Wrappers.<ResourceAdoptionItem>lambdaUpdate()
                        .eq(ResourceAdoptionItem::getId, item.getId())
                        .eq(ResourceAdoptionItem::getStatus, ResourceAdoptionItemStatus.READING.name())
                        .set(ResourceAdoptionItem::getResourceId, resource.getId())
                        .set(ResourceAdoptionItem::getSourceHash, adoption.contentHash())
                        .set(ResourceAdoptionItem::getSnapshotSize, adoption.size())
                        .set(ResourceAdoptionItem::getHashBaselined, baselined)
                        .set(ResourceAdoptionItem::getStatus, ResourceAdoptionItemStatus.ADOPTED.name())
                        .set(ResourceAdoptionItem::getErrorMessage, null)
        );
        if (itemUpdated != 1) {
            throw new ConcurrentModificationException("接管条目提交状态已变化");
        }

        resource.setPublicId(publicId);
        resource.setPath(stablePath);
        resource.setActiveLocationId(activeLocation.getId());
        resource.setLocationVersion(nextVersion);
        resource.setResourceHash(adoption.contentHash());
        resource.setHashVerifiedAt(activeVerifiedAt);
        resource.setStoreType(activeLocation.getStoreType());
        resource.setStorageKey(activeLocation.getStorageKey());
        resource.setSize(Math.toIntExact(adoption.size()));
        resource.setMimeType(adoption.mimeType());
        return new CommitResult(resource, activeLocation, replacement, baselined, false);
    }

    private Resource createResource(VerifiedAdoption adoption) {
        Resource resource = new Resource();
        String publicId = UUID.randomUUID().toString().replace("-", "").toLowerCase(Locale.ROOT);
        resource.setPublicId(publicId);
        resource.setUserId(adoption.ownerId());
        resource.setType("legacyAdopted");
        resource.setPath(resourceLocationService.stablePath(publicId));
        resource.setSize(Math.toIntExact(adoption.size()));
        resource.setOriginalName(adoption.originalName());
        resource.setMimeType(adoption.mimeType());
        resource.setResourceHash(adoption.contentHash());
        resource.setHashSource("LEGACY_ADOPTION");
        resource.setHashVerifiedAt(adoption.verifiedAt());
        resource.setStatus(true);
        resource.setStoreType(adoption.storeType());
        resource.setStorageKey(adoption.storageKey());
        resource.setLocationVersion(0);
        resource.setContentState(ResourceContentState.ACTIVE.name());
        try {
            if (resourceMapper.insert(resource) != 1 || resource.getId() == null) {
                throw new IllegalStateException("历史逻辑资源创建失败");
            }
            return resource;
        } catch (DuplicateKeyException e) {
            throw new ConcurrentModificationException("历史资源在接管期间被并发创建");
        }
    }

    private Resource requireCompatibleResource(Integer resourceId, VerifiedAdoption adoption) {
        Resource resource = resourceMapper.selectByIdForUpdate(resourceId);
        if (resource == null || !Boolean.TRUE.equals(resource.getStatus())) {
            throw new IllegalStateException("已登记逻辑资源不存在或已停用");
        }
        if (!ResourceContentState.isActive(resource.getContentState())) {
            throw new IllegalStateException("资源存在未完成的内容替换，禁止历史接管");
        }
        if (hasVerifiedHash(resource)
                && !hashEquals(resource.getResourceHash(), adoption.contentHash())) {
            throw new IllegalStateException("已登记资源哈希与历史来源完整回读不一致");
        }
        return resource;
    }

    private ResourceLocation createLocation(Integer resourceId,
                                            VerifiedAdoption adoption,
                                            ResourceLocationStatus status) {
        ResourceLocation location = new ResourceLocation();
        location.setResourceId(resourceId);
        location.setStoreType(adoption.storeType());
        location.setStorageKey(adoption.storageKey());
        location.setAccessPath(adoption.accessPath());
        location.setContentHash(adoption.contentHash());
        location.setSize(adoption.size());
        location.setMimeType(adoption.mimeType());
        location.setStatus(status.name());
        location.setVerifiedAt(adoption.verifiedAt());
        try {
            if (locationMapper.insert(location) != 1 || location.getId() == null) {
                throw new IllegalStateException("历史物理副本登记失败");
            }
            return location;
        } catch (DuplicateKeyException e) {
            throw new ConcurrentModificationException("历史物理地址在接管期间被并发登记");
        }
    }

    private ResourceLocation verifyAndRefreshLocation(ResourceLocation location,
                                                      VerifiedAdoption adoption,
                                                      ResourceLocationStatus status) {
        boolean hasVerifiedEvidence = location.getVerifiedAt() != null
                && isSha256(location.getContentHash());
        if (hasVerifiedEvidence
                && !hashEquals(location.getContentHash(), adoption.contentHash())) {
            throw new IllegalStateException("已验证物理副本哈希与完整回读不一致");
        }
        if (hasVerifiedEvidence && !java.util.Objects.equals(location.getSize(), adoption.size())) {
            throw new IllegalStateException("已验证物理副本字节数与完整回读不一致");
        }
        if (ResourceLocationStatus.DELETED.name().equals(location.getStatus())
                || ResourceLocationStatus.MISSING.name().equals(location.getStatus())) {
            throw new IllegalStateException("已删除或缺失的物理副本不能自动重新接管");
        }
        location.setStorageKey(adoption.storageKey());
        location.setContentHash(adoption.contentHash());
        location.setSize(adoption.size());
        location.setMimeType(adoption.mimeType());
        location.setStatus(status.name());
        location.setVerifiedAt(adoption.verifiedAt());
        if (locationMapper.updateById(location) != 1) {
            throw new ConcurrentModificationException("历史物理副本证据更新失败");
        }
        return location;
    }

    private void registerAlias(Integer resourceId, String aliasUrl) {
        ResourceAlias existing = aliasMapper.findActiveByAliasUrlForUpdate(aliasUrl);
        if (existing != null) {
            if (!resourceId.equals(existing.getResourceId())) {
                throw new IllegalStateException("历史资源别名已属于其他逻辑资源");
            }
            return;
        }
        ResourceAlias alias = new ResourceAlias();
        alias.setResourceId(resourceId);
        alias.setAliasUrl(aliasUrl);
        alias.setSourceType("DISCOVERED_REFERENCE");
        alias.setStatus(true);
        try {
            aliasMapper.insert(alias);
        } catch (DuplicateKeyException e) {
            throw new ConcurrentModificationException("历史资源别名在接管期间发生冲突");
        }
    }

    private Integer uniqueResourceId(Integer... values) {
        Set<Integer> ids = new LinkedHashSet<>();
        if (values != null) {
            for (Integer value : values) {
                if (value != null) {
                    ids.add(value);
                }
            }
        }
        if (ids.size() > 1) {
            throw new IllegalStateException("历史URL、物理地址与现有资源记录指向不同逻辑资源");
        }
        return ids.isEmpty() ? null : ids.iterator().next();
    }

    private void markSkipped(ResourceAdoptionItem item, String message) {
        int updated = itemMapper.update(
                null,
                Wrappers.<ResourceAdoptionItem>lambdaUpdate()
                        .eq(ResourceAdoptionItem::getId, item.getId())
                        .eq(ResourceAdoptionItem::getStatus, ResourceAdoptionItemStatus.READING.name())
                        .set(ResourceAdoptionItem::getStatus, ResourceAdoptionItemStatus.SKIPPED.name())
                        .set(ResourceAdoptionItem::getErrorMessage, message)
        );
        if (updated != 1) {
            throw new ConcurrentModificationException("接管条目跳过状态已变化");
        }
    }

    private void validate(VerifiedAdoption adoption) {
        if (adoption == null
                || adoption.itemId() == null
                || adoption.ownerId() == null
                || !StringUtils.hasText(adoption.sourceUrl())
                || !StringUtils.hasText(adoption.storeType())
                || !StringUtils.hasText(adoption.accessPath())
                || !StringUtils.hasText(adoption.originalName())
                || !StringUtils.hasText(adoption.mimeType())
                || adoption.size() <= 0
                || adoption.size() > Integer.MAX_VALUE
                || adoption.verifiedAt() == null
                || !StringUtils.hasText(adoption.contentHash())
                || !adoption.contentHash().matches("(?i)[a-f0-9]{64}")) {
            throw new IllegalArgumentException("历史接管完整回读证据不完整");
        }
    }

    private boolean hasVerifiedHash(Resource resource) {
        return resource != null
                && resource.getHashVerifiedAt() != null
                && isSha256(resource.getResourceHash());
    }

    private boolean isSha256(String value) {
        return StringUtils.hasText(value) && value.matches("(?i)[a-f0-9]{64}");
    }

    private boolean hashEquals(String left, String right) {
        return StringUtils.hasText(left)
                && StringUtils.hasText(right)
                && left.equalsIgnoreCase(right);
    }

    private int normalizeVersion(Integer version) {
        return version == null ? 0 : version;
    }

    public record VerifiedAdoption(
            Long itemId,
            Integer ownerId,
            String sourceUrl,
            String storeType,
            String storageKey,
            String accessPath,
            String originalName,
            String contentHash,
            long size,
            String mimeType,
            LocalDateTime verifiedAt
    ) {
    }

    public record CommitResult(
            Resource resource,
            ResourceLocation location,
            ResourceReferenceService.ReplacementResult replacement,
            boolean hashBaselined,
            boolean skipped
    ) {
        private static CommitResult skippedResult() {
            return new CommitResult(null, null, null, false, true);
        }
    }
}