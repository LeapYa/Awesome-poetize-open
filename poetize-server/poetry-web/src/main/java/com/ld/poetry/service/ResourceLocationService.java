package com.ld.poetry.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ld.poetry.dao.ResourceAliasMapper;
import com.ld.poetry.dao.ResourceLocationMapper;
import com.ld.poetry.dao.ResourceMapper;
import com.ld.poetry.entity.Resource;
import com.ld.poetry.entity.ResourceAlias;
import com.ld.poetry.entity.ResourceLocation;
import com.ld.poetry.enums.ResourceContentState;
import com.ld.poetry.enums.ResourceLocationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ConcurrentModificationException;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ResourceLocationService {

    private static final String MEDIA_PREFIX = "/media/";

    private final ResourceService resourceService;
    private final ResourceMapper resourceMapper;
    private final ResourceLocationMapper resourceLocationMapper;
    private final ResourceAliasMapper resourceAliasMapper;

    @Transactional(rollbackFor = Exception.class)
    public RegisteredUpload registerVerifiedUpload(VerifiedUpload upload) {
        validateVerifiedUpload(upload);
        String normalizedStoreType = normalizeStoreType(upload.storeType());
        String normalizedHash = requireHash(upload.contentHash(), "上传目标完整回读SHA-256不合法");
        LocalDateTime verifiedAt = upload.verifiedAt();

        ResourceLocation existingLocation = findLocation(normalizedStoreType, upload.accessPath());
        if (existingLocation != null) {
            return reuseVerifiedUpload(existingLocation, upload, normalizedHash);
        }

        String publicId = newPublicId();
        Resource resource = new Resource();
        resource.setPublicId(publicId);
        resource.setUserId(upload.userId());
        resource.setType(upload.type());
        resource.setPath(stablePath(publicId));
        resource.setSize(Math.toIntExact(upload.size()));
        resource.setOriginalName(upload.originalName());
        resource.setMimeType(upload.mimeType());
        resource.setResourceHash(normalizedHash);
        resource.setHashSource("UPLOAD_READBACK");
        resource.setHashVerifiedAt(verifiedAt);
        resource.setWidth(upload.width());
        resource.setHeight(upload.height());
        resource.setStatus(true);
        resource.setStoreType(normalizedStoreType);
        resource.setStorageKey(upload.storageKey());
        resource.setLocationVersion(0);
        resource.setContentState(ResourceContentState.ACTIVE.name());
        if (resourceMapper.insert(resource) != 1 || resource.getId() == null) {
            throw new IllegalStateException("逻辑资源创建失败");
        }

        ResourceLocation location = new ResourceLocation();
        location.setResourceId(resource.getId());
        location.setStoreType(normalizedStoreType);
        location.setStorageKey(upload.storageKey());
        location.setAccessPath(upload.accessPath());
        location.setContentHash(normalizedHash);
        location.setSize(upload.size());
        location.setMimeType(upload.mimeType());
        location.setStatus(ResourceLocationStatus.ACTIVE.name());
        location.setVerifiedAt(verifiedAt);
        if (resourceLocationMapper.insert(location) != 1 || location.getId() == null) {
            throw new IllegalStateException("上传物理副本登记失败");
        }

        int linked = resourceMapper.update(
                null,
                Wrappers.<Resource>lambdaUpdate()
                        .eq(Resource::getId, resource.getId())
                        .eq(Resource::getPublicId, publicId)
                        .eq(Resource::getPath, stablePath(publicId))
                        .isNull(Resource::getActiveLocationId)
                        .eq(Resource::getLocationVersion, 0)
                        .eq(Resource::getContentState, ResourceContentState.ACTIVE.name())
                        .eq(Resource::getResourceHash, normalizedHash)
                        .set(Resource::getActiveLocationId, location.getId())
        );
        if (linked != 1) {
            throw new ConcurrentModificationException("上传活动物理副本关联失败");
        }
        registerAlias(resource.getId(), upload.accessPath(), "UPLOAD_ORIGIN");
        resource.setActiveLocationId(location.getId());
        return new RegisteredUpload(resource, location, false);
    }

    private RegisteredUpload reuseVerifiedUpload(ResourceLocation location,
                                                  VerifiedUpload upload,
                                                  String normalizedHash) {
        if (!ResourceLocationStatus.ACTIVE.name().equals(location.getStatus())
                || location.getVerifiedAt() == null
                || !hashEquals(location.getContentHash(), normalizedHash)
                || !java.util.Objects.equals(location.getSize(), upload.size())
                || !java.util.Objects.equals(location.getStorageKey(), upload.storageKey())) {
            throw new IllegalStateException("上传物理地址已存在，但其可信内容证据不一致");
        }
        Resource resource = resourceMapper.selectById(location.getResourceId());
        if (resource == null
                || !Boolean.TRUE.equals(resource.getStatus())
                || !ResourceContentState.isActive(resource.getContentState())
                || !java.util.Objects.equals(resource.getUserId(), upload.userId())
                || !java.util.Objects.equals(resource.getType(), upload.type())
                || !java.util.Objects.equals(resource.getActiveLocationId(), location.getId())
                || !hashEquals(resource.getResourceHash(), normalizedHash)
                || resource.getHashVerifiedAt() == null
                || !StringUtils.hasText(resource.getPublicId())
                || !stablePath(resource.getPublicId()).equals(resource.getPath())) {
            throw new IllegalStateException("上传物理地址已属于其他逻辑资源或状态不一致");
        }
        return new RegisteredUpload(resource, location, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public Resource updateStableMetadata(String path,
                                         Integer userId,
                                         String type,
                                         String originalName) {
        if (userId == null || !StringUtils.hasText(path) || !StringUtils.hasText(type)) {
            throw new IllegalArgumentException("稳定资源路径、资源类型和用户不能为空");
        }
        String normalizedType = type.trim();
        if (normalizedType.length() > 32) {
            throw new IllegalArgumentException("资源类型不合法");
        }
        String normalizedName = StringUtils.hasText(originalName) ? originalName.trim() : null;
        if (normalizedName != null && normalizedName.length() > 512) {
            throw new IllegalArgumentException("资源原始名称不合法");
        }

        String publicId = publicIdFromStablePath(path);
        Resource resource = resourceMapper.findByPublicIdForUpdate(publicId);
        if (resource == null || !path.equals(resource.getPath())) {
            throw new IllegalArgumentException("稳定资源不存在");
        }
        if (!java.util.Objects.equals(resource.getUserId(), userId)) {
            throw new IllegalStateException("无权更新其他用户的稳定资源元数据");
        }
        if (!Boolean.TRUE.equals(resource.getStatus())
                || !ResourceContentState.isActive(resource.getContentState())
                || resource.getActiveLocationId() == null
                || resource.getHashVerifiedAt() == null
                || !StringUtils.hasText(resource.getResourceHash())) {
            throw new IllegalStateException("稳定资源状态或内容证据不完整");
        }
        ResourceLocation activeLocation = resourceLocationMapper.selectById(resource.getActiveLocationId());
        if (activeLocation == null
                || !java.util.Objects.equals(activeLocation.getResourceId(), resource.getId())
                || !ResourceLocationStatus.ACTIVE.name().equals(activeLocation.getStatus())
                || activeLocation.getVerifiedAt() == null
                || !hashEquals(activeLocation.getContentHash(), resource.getResourceHash())) {
            throw new IllegalStateException("稳定资源活动副本状态不一致");
        }

        var update = Wrappers.<Resource>lambdaUpdate()
                .eq(Resource::getId, resource.getId())
                .eq(Resource::getPublicId, publicId)
                .eq(Resource::getPath, path)
                .eq(Resource::getUserId, userId)
                .set(Resource::getType, normalizedType);
        if (normalizedName != null) {
            update.set(Resource::getOriginalName, normalizedName);
        }
        if (resourceMapper.update(null, update) != 1) {
            throw new ConcurrentModificationException("稳定资源逻辑元数据更新失败");
        }
        resource.setType(normalizedType);
        if (normalizedName != null) {
            resource.setOriginalName(normalizedName);
        }
        return resource;
    }

    private void validateVerifiedUpload(VerifiedUpload upload) {
        if (upload == null
                || upload.userId() == null
                || !StringUtils.hasText(upload.type())
                || !StringUtils.hasText(upload.storeType())
                || !StringUtils.hasText(upload.storageKey())
                || !StringUtils.hasText(upload.accessPath())
                || !StringUtils.hasText(upload.originalName())
                || !StringUtils.hasText(upload.mimeType())
                || upload.size() <= 0
                || upload.size() > Integer.MAX_VALUE
                || upload.verifiedAt() == null) {
            throw new IllegalArgumentException("上传资源登记信息不完整");
        }
        requireHash(upload.contentHash(), "上传目标完整回读SHA-256不合法");
    }

    @Transactional(rollbackFor = Exception.class)
    public Resource ensureManaged(Integer resourceId) {
        Resource resource = resourceMapper.selectByIdForUpdate(resourceId);
        if (resource == null) {
            throw new IllegalArgumentException("资源不存在：" + resourceId);
        }
        requireActiveContent(resource, "资源存在未完成的内容替换，暂不能登记物理副本");
        if (!StringUtils.hasText(resource.getPublicId())) {
            String publicId = newPublicId();
            boolean updated = resourceService.lambdaUpdate()
                    .eq(Resource::getId, resourceId)
                    .and(wrapper -> wrapper.isNull(Resource::getPublicId).or().eq(Resource::getPublicId, ""))
                    .set(Resource::getPublicId, publicId)
                    .update();
            resource = requireResource(resourceId);
            if (!updated && !StringUtils.hasText(resource.getPublicId())) {
                throw new ConcurrentModificationException("资源稳定ID生成失败");
            }
        }

        if (resource.getActiveLocationId() == null && isPhysicalPath(resource.getPath())) {
            ResourceLocation location = registerLocation(
                    resource,
                    resource.getStoreType(),
                    resource.getStorageKey(),
                    resource.getPath(),
                    resource.getResourceHash(),
                    resource.getSize() == null ? null : resource.getSize().longValue(),
                    resource.getMimeType(),
                    ResourceLocationStatus.ACTIVE,
                    null
            );
            boolean linked = resourceService.lambdaUpdate()
                    .eq(Resource::getId, resourceId)
                    .isNull(Resource::getActiveLocationId)
                    .set(Resource::getActiveLocationId, location.getId())
                    .set(Resource::getLocationVersion, 0)
                    .update();
            resource = requireResource(resourceId);
            if (!linked && resource.getActiveLocationId() == null) {
                throw new ConcurrentModificationException("资源活动副本登记失败");
            }
        }

        if (isPhysicalPath(resource.getPath())) {
            registerAlias(resourceId, resource.getPath(), "CURRENT_PATH");
        }
        return requireResource(resourceId);
    }

    public ResourceLocation requireActiveLocation(Resource resource) {
        if (resource == null || resource.getActiveLocationId() == null) {
            throw new IllegalStateException("资源尚未登记活动物理副本");
        }
        requireActiveContent(resource, "资源存在未完成的内容替换，活动物理副本暂不可用");
        ResourceLocation location = requireLocation(resource.getId(), resource.getActiveLocationId());
        if (!ResourceLocationStatus.ACTIVE.name().equals(location.getStatus())) {
            throw new IllegalStateException("资源活动物理副本状态不一致");
        }
        return location;
    }

    public ResourceLocation requireLocation(Integer resourceId, Long locationId) {
        if (resourceId == null || locationId == null) {
            throw new IllegalArgumentException("资源ID和物理副本ID不能为空");
        }
        ResourceLocation location = resourceLocationMapper.selectById(locationId);
        if (location == null || !resourceId.equals(location.getResourceId())) {
            throw new IllegalStateException("物理副本不存在或不属于当前资源");
        }
        return location;
    }

    @Transactional(rollbackFor = Exception.class)
    public LocationDeletionClaim claimLocationDeletion(Integer resourceId,
                                                        Long locationId,
                                                        Long replacementLocationId) {
        if (resourceId == null || locationId == null) {
            throw new IllegalArgumentException("资源ID和物理副本ID不能为空");
        }
        Resource resource = resourceMapper.selectByIdForUpdate(resourceId);
        if (resource == null) {
            throw new IllegalArgumentException("资源不存在：" + resourceId);
        }
        requireActiveContent(resource, "资源存在未完成的内容替换，不能删除物理副本");

        ResourceLocation location = resourceLocationMapper.selectByIdForUpdate(locationId);
        if (location == null || !resourceId.equals(location.getResourceId())) {
            throw new IllegalStateException("物理副本不存在或不属于当前资源");
        }
        boolean activeByResource = locationId.equals(resource.getActiveLocationId());
        boolean activeByLocation = ResourceLocationStatus.ACTIVE.name().equals(location.getStatus());
        if (activeByResource != activeByLocation) {
            throw new IllegalStateException("资源活动副本与物理副本状态不一致");
        }
        if (activeByResource) {
            activateReplacementForDeletion(resourceId, locationId, replacementLocationId);
        } else if (replacementLocationId != null) {
            throw new IllegalArgumentException("删除非活动副本时不能指定替代副本");
        }
        return claimInactiveLocationDeletion(resourceId, locationId, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public LocationDeletionClaim reclaimStaleLocationDeletion(Integer resourceId,
                                                               Long locationId,
                                                               LocalDateTime staleBefore) {
        if (resourceId == null || locationId == null || staleBefore == null) {
            throw new IllegalArgumentException("资源ID、物理副本ID和删除租约时间不能为空");
        }
        Resource resource = resourceMapper.selectByIdForUpdate(resourceId);
        if (resource == null) {
            throw new IllegalArgumentException("资源不存在：" + resourceId);
        }
        boolean logicalDeletion = !Boolean.TRUE.equals(resource.getStatus())
                && ResourceContentState.DELETION_PENDING.name().equals(resource.getContentState());
        if (!logicalDeletion) {
            requireActiveContent(resource, "资源存在未完成的内容替换，不能恢复物理副本清理");
        }

        ResourceLocation location = resourceLocationMapper.selectByIdForUpdate(locationId);
        if (location == null || !resourceId.equals(location.getResourceId())) {
            throw new IllegalStateException("物理副本不存在或不属于当前资源");
        }
        if (!logicalDeletion
                && (locationId.equals(resource.getActiveLocationId())
                || ResourceLocationStatus.ACTIVE.name().equals(location.getStatus()))) {
            throw new IllegalStateException("活动物理副本不能处于删除恢复流程");
        }
        if (!ResourceLocationStatus.DELETING.name().equals(location.getStatus())) {
            return new LocationDeletionClaim(resource, location, false, location.getStatus());
        }
        if (location.getUpdateTime() == null || location.getUpdateTime().isAfter(staleBefore)) {
            return new LocationDeletionClaim(resource, location, false, location.getStatus());
        }

        LocalDateTime claimedAt = nextDeletionClaimTime(location.getUpdateTime());
        int updated = resourceLocationMapper.update(
                null,
                Wrappers.<ResourceLocation>lambdaUpdate()
                        .eq(ResourceLocation::getId, locationId)
                        .eq(ResourceLocation::getResourceId, resourceId)
                        .eq(ResourceLocation::getStatus, ResourceLocationStatus.DELETING.name())
                        .le(ResourceLocation::getUpdateTime, staleBefore)
                        .set(ResourceLocation::getUpdateTime, claimedAt)
        );
        if (updated != 1) {
            throw new ConcurrentModificationException("物理副本删除恢复租约已被其他请求取得");
        }
        location.setUpdateTime(claimedAt);
        // 逻辑删除中的活动副本原本是 ACTIVE（ResourceDeletionStateService.claim 直接把活动副本转为 DELETING，
        // 不会先切换替代副本），恢复时必须保留 ACTIVE 语义，否则会形成“活动指针指向 RETAINED”的不一致；
        // 非活动副本的原始状态在转为 DELETING 后无法从行内恢复，保守保留为 RETAINED（已验证的保留副本）。
        String restoredStatus = (logicalDeletion && locationId.equals(resource.getActiveLocationId()))
                ? ResourceLocationStatus.ACTIVE.name()
                : ResourceLocationStatus.RETAINED.name();
        return new LocationDeletionClaim(
                resource,
                location,
                true,
                restoredStatus,
                claimedAt
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public LocationDeletionClaim claimRetainedLocationDeletion(Integer resourceId, Long locationId) {
        return claimInactiveLocationDeletion(resourceId, locationId, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public LocationDeletionClaim claimInactiveLocationDeletion(Integer resourceId, Long locationId) {
        return claimInactiveLocationDeletion(resourceId, locationId, false);
    }

    private LocationDeletionClaim claimInactiveLocationDeletion(Integer resourceId,
                                                                 Long locationId,
                                                                 boolean retainedOnly) {
        if (resourceId == null || locationId == null) {
            throw new IllegalArgumentException("资源ID和物理副本ID不能为空");
        }
        Resource resource = resourceMapper.selectByIdForUpdate(resourceId);
        if (resource == null) {
            throw new IllegalArgumentException("资源不存在：" + resourceId);
        }
        requireActiveContent(resource, "资源存在未完成的内容替换，不能清理物理副本");

        ResourceLocation location = resourceLocationMapper.selectByIdForUpdate(locationId);
        if (location == null || !resourceId.equals(location.getResourceId())) {
            throw new IllegalStateException("物理副本不存在或不属于当前资源");
        }
        if (locationId.equals(resource.getActiveLocationId())
                || ResourceLocationStatus.ACTIVE.name().equals(location.getStatus())) {
            throw new IllegalStateException("活动物理副本不能直接清理，请先切换到已验证的替代副本");
        }
        if (ResourceLocationStatus.DELETED.name().equals(location.getStatus())
                || ResourceLocationStatus.MISSING.name().equals(location.getStatus())
                || ResourceLocationStatus.DELETING.name().equals(location.getStatus())) {
            return new LocationDeletionClaim(resource, location, false, location.getStatus());
        }

        String originalStatus = location.getStatus();
        boolean deletable = ResourceLocationStatus.RETAINED.name().equals(originalStatus)
                || (!retainedOnly && (ResourceLocationStatus.STAGED.name().equals(originalStatus)
                || ResourceLocationStatus.STALE.name().equals(originalStatus)));
        if (!deletable) {
            throw new IllegalStateException(retainedOnly
                    ? "只有保留状态的非活动物理副本可以清理"
                    : "只有保留、暂存或失效状态的非活动物理副本可以清理");
        }

        LocalDateTime claimedAt = nextDeletionClaimTime(location.getUpdateTime());
        int updated = resourceLocationMapper.update(
                null,
                Wrappers.<ResourceLocation>lambdaUpdate()
                        .eq(ResourceLocation::getId, locationId)
                        .eq(ResourceLocation::getResourceId, resourceId)
                        .eq(ResourceLocation::getStatus, originalStatus)
                        .set(ResourceLocation::getStatus, ResourceLocationStatus.DELETING.name())
                        .set(ResourceLocation::getUpdateTime, claimedAt)
        );
        if (updated != 1) {
            throw new ConcurrentModificationException("物理副本删除声明期间状态已变化");
        }
        location.setStatus(ResourceLocationStatus.DELETING.name());
        location.setUpdateTime(claimedAt);
        return new LocationDeletionClaim(resource, location, true, originalStatus, claimedAt);
    }

    @Transactional(rollbackFor = Exception.class)
    public void completeLocationDeletion(Integer resourceId,
                                         Long locationId,
                                         LocalDateTime claimedAt,
                                         boolean missing) {
        requireDeletionClaim(claimedAt);
        String targetStatus = missing
                ? ResourceLocationStatus.MISSING.name()
                : ResourceLocationStatus.DELETED.name();
        int updated = resourceLocationMapper.update(
                null,
                Wrappers.<ResourceLocation>lambdaUpdate()
                        .eq(ResourceLocation::getId, locationId)
                        .eq(ResourceLocation::getResourceId, resourceId)
                        .eq(ResourceLocation::getStatus, ResourceLocationStatus.DELETING.name())
                        .eq(ResourceLocation::getUpdateTime, claimedAt)
                        .set(ResourceLocation::getStatus, targetStatus)
        );
        if (updated != 1) {
            ResourceLocation latest = requireLocation(resourceId, locationId);
            if (!targetStatus.equals(latest.getStatus())) {
                throw new ConcurrentModificationException("物理副本删除收尾状态或租约已变化");
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void restoreLocationDeletion(Integer resourceId,
                                        Long locationId,
                                        LocalDateTime claimedAt,
                                        String originalStatus) {
        requireDeletionClaim(claimedAt);
        boolean restoringActive = ResourceLocationStatus.ACTIVE.name().equals(originalStatus);
        if (!restoringActive
                && !ResourceLocationStatus.RETAINED.name().equals(originalStatus)
                && !ResourceLocationStatus.STAGED.name().equals(originalStatus)
                && !ResourceLocationStatus.STALE.name().equals(originalStatus)) {
            throw new IllegalArgumentException("物理副本删除恢复状态不合法");
        }
        if (restoringActive) {
            Resource resource = resourceMapper.selectByIdForUpdate(resourceId);
            if (resource == null
                    || Boolean.TRUE.equals(resource.getStatus())
                    || !ResourceContentState.DELETION_PENDING.name().equals(resource.getContentState())
                    || !locationId.equals(resource.getActiveLocationId())) {
                throw new IllegalStateException("逻辑删除中的活动副本不能恢复为可用状态");
            }
        }
        int updated = resourceLocationMapper.update(
                null,
                Wrappers.<ResourceLocation>lambdaUpdate()
                        .eq(ResourceLocation::getId, locationId)
                        .eq(ResourceLocation::getResourceId, resourceId)
                        .eq(ResourceLocation::getStatus, ResourceLocationStatus.DELETING.name())
                        .eq(ResourceLocation::getUpdateTime, claimedAt)
                        .set(ResourceLocation::getStatus, originalStatus)
        );
        if (updated != 1) {
            ResourceLocation latest = requireLocation(resourceId, locationId);
            if (!originalStatus.equals(latest.getStatus())) {
                throw new ConcurrentModificationException("物理副本删除失败恢复状态或租约已变化");
            }
        }
    }

    private LocalDateTime nextDeletionClaimTime(LocalDateTime previous) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        if (previous == null || now.isAfter(previous)) {
            return now;
        }
        return previous.plusSeconds(1);
    }

    private void requireDeletionClaim(LocalDateTime claimedAt) {
        if (claimedAt == null) {
            throw new IllegalArgumentException("物理副本删除租约不能为空");
        }
    }

    public record LocationDeletionClaim(
            Resource resource,
            ResourceLocation location,
            boolean claimed,
            String originalStatus,
            LocalDateTime claimedAt
    ) {
        public LocationDeletionClaim(Resource resource,
                                     ResourceLocation location,
                                     boolean claimed,
                                     String originalStatus) {
            this(resource, location, claimed, originalStatus, null);
        }

        public LocationDeletionClaim(Resource resource,
                                     ResourceLocation location,
                                     boolean claimed) {
            this(
                    resource,
                    location,
                    claimed,
                    location == null ? null : location.getStatus(),
                    null
            );
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public ResourceLocation stageLocation(Integer resourceId,
                                          Long expectedSourceLocationId,
                                          Integer expectedVersion,
                                          String expectedContentHash,
                                          String storeType,
                                          String storageKey,
                                          String accessPath,
                                          String contentHash,
                                          Long size,
                                          String mimeType,
                                          LocalDateTime verifiedAt) {
        String normalizedExpectedHash = requireHash(expectedContentHash, "资源基准SHA-256不合法");
        String normalizedContentHash = requireHash(contentHash, "目标完整回读SHA-256不合法");
        if (!hashEquals(normalizedExpectedHash, normalizedContentHash)) {
            throw new IllegalStateException("目标完整回读哈希与资源基准哈希不一致");
        }
        if (verifiedAt == null) {
            throw new IllegalArgumentException("目标完整回读校验时间不能为空");
        }

        Resource resource = resourceMapper.selectByIdForUpdate(resourceId);
        if (resource == null) {
            throw new IllegalArgumentException("资源不存在：" + resourceId);
        }
        requireActiveContent(resource, "资源存在未完成的内容替换，不能登记迁移目标");
        if (!java.util.Objects.equals(resource.getActiveLocationId(), expectedSourceLocationId)
                || normalizeVersion(resource.getLocationVersion()) != normalizeVersion(expectedVersion)) {
            throw new ConcurrentModificationException("资源活动副本在目标登记期间发生变化");
        }
        if (!hashEquals(normalizedExpectedHash, resource.getResourceHash())) {
            throw new ConcurrentModificationException("资源内容哈希在目标登记期间发生变化");
        }
        return stageVerifiedLocation(
                resource,
                expectedSourceLocationId,
                storeType,
                storageKey,
                accessPath,
                normalizedContentHash,
                size,
                mimeType,
                verifiedAt
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public Resource activateReplacementForDeletion(Integer resourceId,
                                                   Long expectedActiveLocationId,
                                                   Long replacementLocationId) {
        if (replacementLocationId == null || replacementLocationId.equals(expectedActiveLocationId)) {
            throw new IllegalArgumentException("删除活动副本前必须指定不同的替代副本");
        }
        Resource resource = resourceMapper.selectByIdForUpdate(resourceId);
        if (resource == null) {
            throw new IllegalArgumentException("资源不存在：" + resourceId);
        }
        requireActiveContent(resource, "资源存在未完成的内容替换，不能切换活动副本");
        if (!java.util.Objects.equals(resource.getActiveLocationId(), expectedActiveLocationId)) {
            throw new ConcurrentModificationException("资源活动副本已变化");
        }
        ResourceLocation replacement = resourceLocationMapper.selectByIdForUpdate(replacementLocationId);
        if (replacement == null || !resourceId.equals(replacement.getResourceId())) {
            throw new IllegalArgumentException("替代物理副本不属于当前资源");
        }
        if (!ResourceLocationStatus.RETAINED.name().equals(replacement.getStatus())
                || replacement.getVerifiedAt() == null
                || !hashEquals(replacement.getContentHash(), resource.getResourceHash())) {
            throw new IllegalStateException("替代物理副本必须是已完整回读验证的保留副本");
        }
        return activate(
                resourceId,
                expectedActiveLocationId,
                resource.getLocationVersion(),
                resource.getResourceHash(),
                replacementLocationId
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public Resource activate(Integer resourceId,
                             Long expectedSourceLocationId,
                             Integer expectedVersion,
                             String expectedContentHash,
                             Long targetLocationId) {
        Resource resource = resourceMapper.selectByIdForUpdate(resourceId);
        if (resource == null) {
            throw new IllegalArgumentException("资源不存在：" + resourceId);
        }
        requireActiveContent(resource, "资源存在未完成的内容替换，不能切换活动副本");
        if (!java.util.Objects.equals(resource.getActiveLocationId(), expectedSourceLocationId)
                || !java.util.Objects.equals(normalizeVersion(resource.getLocationVersion()), normalizeVersion(expectedVersion))) {
            throw new ConcurrentModificationException("资源活动副本已变化");
        }
        if (StringUtils.hasText(expectedContentHash)
                && !hashEquals(expectedContentHash, resource.getResourceHash())) {
            throw new ConcurrentModificationException("资源内容哈希已变化");
        }

        ResourceLocation target = resourceLocationMapper.selectById(targetLocationId);
        if (target == null || !resourceId.equals(target.getResourceId())) {
            throw new IllegalArgumentException("目标物理副本不属于当前资源");
        }
        if (!StringUtils.hasText(target.getContentHash())
                || !hashEquals(target.getContentHash(), resource.getResourceHash())) {
            throw new IllegalStateException("目标物理副本未通过内容哈希校验");
        }
        if (target.getVerifiedAt() == null) {
            throw new IllegalStateException("目标物理副本尚未完成完整回读校验");
        }

        var resourceUpdate = resourceService.lambdaUpdate()
                .eq(Resource::getId, resourceId)
                .eq(Resource::getActiveLocationId, expectedSourceLocationId)
                .eq(Resource::getLocationVersion, normalizeVersion(expectedVersion))
                .eq(Resource::getContentState, ResourceContentState.ACTIVE.name());
        if (StringUtils.hasText(expectedContentHash)) {
            resourceUpdate.eq(Resource::getResourceHash, expectedContentHash);
        }
        boolean switched = resourceUpdate
                .set(Resource::getActiveLocationId, targetLocationId)
                .set(Resource::getLocationVersion, normalizeVersion(expectedVersion) + 1)
                .set(Resource::getStoreType, target.getStoreType())
                .set(Resource::getStorageKey, target.getStorageKey())
                .set(Resource::getHashVerifiedAt, target.getVerifiedAt())
                .update();
        if (!switched) {
            throw new ConcurrentModificationException("资源活动副本切换期间发生并发修改");
        }

        if (!targetLocationId.equals(expectedSourceLocationId)) {
            int sourceUpdated = resourceLocationMapper.update(
                    null,
                    Wrappers.<ResourceLocation>lambdaUpdate()
                            .eq(ResourceLocation::getId, expectedSourceLocationId)
                            .eq(ResourceLocation::getResourceId, resourceId)
                            .eq(ResourceLocation::getStatus, ResourceLocationStatus.ACTIVE.name())
                            .set(ResourceLocation::getStatus, ResourceLocationStatus.RETAINED.name())
            );
            if (sourceUpdated != 1) {
                throw new ConcurrentModificationException("源物理副本状态已变化");
            }
        }

        int targetUpdated = resourceLocationMapper.update(
                null,
                Wrappers.<ResourceLocation>lambdaUpdate()
                        .eq(ResourceLocation::getId, targetLocationId)
                        .eq(ResourceLocation::getResourceId, resourceId)
                        .in(ResourceLocation::getStatus,
                                ResourceLocationStatus.STAGED.name(),
                                ResourceLocationStatus.RETAINED.name())
                        .eq(ResourceLocation::getContentHash, target.getContentHash())
                        .set(ResourceLocation::getStatus, ResourceLocationStatus.ACTIVE.name())
        );
        if (targetUpdated != 1) {
            throw new ConcurrentModificationException("目标物理副本状态已变化");
        }
        return requireResource(resourceId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Resource normalizeStablePath(Integer resourceId, String expectedPath) {
        Resource resource = ensureManaged(resourceId);
        if (!java.util.Objects.equals(expectedPath, resource.getPath())) {
            throw new ConcurrentModificationException("资源路径已变化");
        }
        String stablePath = stablePath(resource.getPublicId());
        if (stablePath.equals(resource.getPath())) {
            return resource;
        }
        registerAlias(resourceId, resource.getPath(), "CURRENT_PATH");
        boolean updated = resourceService.lambdaUpdate()
                .eq(Resource::getId, resourceId)
                .eq(Resource::getPath, expectedPath)
                .set(Resource::getPath, stablePath)
                .update();
        if (!updated) {
            throw new ConcurrentModificationException("资源稳定地址切换期间发生并发修改");
        }
        return requireResource(resourceId);
    }

    @Transactional(rollbackFor = Exception.class)
    public ResourceAlias registerAlias(Integer resourceId, String aliasUrl, String sourceType) {
        if (!StringUtils.hasText(aliasUrl)) {
            throw new IllegalArgumentException("资源别名不能为空");
        }
        ResourceAlias existing = resourceAliasMapper.findActiveByAliasUrl(aliasUrl);
        if (existing != null) {
            if (!resourceId.equals(existing.getResourceId())) {
                throw new IllegalStateException("资源别名已属于其他资源");
            }
            return existing;
        }

        ResourceAlias alias = new ResourceAlias();
        alias.setResourceId(resourceId);
        alias.setAliasUrl(aliasUrl);
        alias.setSourceType(StringUtils.hasText(sourceType) ? sourceType : "CURRENT_PATH");
        alias.setStatus(true);
        try {
            resourceAliasMapper.insert(alias);
            return alias;
        } catch (DuplicateKeyException e) {
            ResourceAlias concurrent = resourceAliasMapper.findActiveByAliasUrl(aliasUrl);
            if (concurrent != null && resourceId.equals(concurrent.getResourceId())) {
                return concurrent;
            }
            throw new IllegalStateException("资源别名发生冲突", e);
        }
    }

    public String stablePath(String publicId) {
        if (!StringUtils.hasText(publicId) || !publicId.matches("[a-fA-F0-9]{32}")) {
            throw new IllegalArgumentException("资源稳定ID不合法");
        }
        return MEDIA_PREFIX + publicId.toLowerCase(Locale.ROOT);
    }

    private String publicIdFromStablePath(String path) {
        if (!StringUtils.hasText(path) || !path.startsWith(MEDIA_PREFIX)) {
            throw new IllegalArgumentException("稳定资源路径不合法");
        }
        String publicId = path.substring(MEDIA_PREFIX.length());
        String normalizedPath = stablePath(publicId);
        if (!normalizedPath.equals(path)) {
            throw new IllegalArgumentException("稳定资源路径不合法");
        }
        return publicId;
    }

    private ResourceLocation stageVerifiedLocation(Resource resource,
                                                    Long sourceLocationId,
                                                    String storeType,
                                                    String storageKey,
                                                    String accessPath,
                                                    String contentHash,
                                                    Long size,
                                                    String mimeType,
                                                    LocalDateTime verifiedAt) {
        String normalizedStoreType = normalizeStoreType(storeType);
        if (!StringUtils.hasText(storageKey) || !StringUtils.hasText(accessPath)) {
            throw new IllegalArgumentException("目标对象键和物理访问地址不能为空");
        }

        ResourceLocation existing = findLocation(normalizedStoreType, accessPath);
        if (existing != null) {
            return restageVerifiedLocation(
                    resource,
                    sourceLocationId,
                    existing,
                    storageKey,
                    contentHash,
                    size,
                    mimeType,
                    verifiedAt
            );
        }

        ResourceLocation location = new ResourceLocation();
        location.setResourceId(resource.getId());
        location.setStoreType(normalizedStoreType);
        location.setStorageKey(storageKey);
        location.setAccessPath(accessPath);
        location.setContentHash(contentHash);
        location.setSize(size);
        location.setMimeType(mimeType);
        location.setStatus(ResourceLocationStatus.STAGED.name());
        location.setVerifiedAt(verifiedAt);
        try {
            resourceLocationMapper.insert(location);
            return location;
        } catch (DuplicateKeyException e) {
            ResourceLocation concurrent = findLocation(normalizedStoreType, accessPath);
            if (concurrent == null) {
                throw new IllegalStateException("物理副本地址摘要冲突", e);
            }
            return restageVerifiedLocation(
                    resource,
                    sourceLocationId,
                    concurrent,
                    storageKey,
                    contentHash,
                    size,
                    mimeType,
                    verifiedAt
            );
        }
    }

    private ResourceLocation restageVerifiedLocation(Resource resource,
                                                      Long sourceLocationId,
                                                      ResourceLocation existing,
                                                      String storageKey,
                                                      String contentHash,
                                                      Long size,
                                                      String mimeType,
                                                      LocalDateTime verifiedAt) {
        if (!resource.getId().equals(existing.getResourceId())) {
            throw new IllegalStateException("物理副本地址已属于其他资源");
        }
        if (sourceLocationId.equals(existing.getId())
                || ResourceLocationStatus.ACTIVE.name().equals(existing.getStatus())) {
            throw new ConcurrentModificationException("目标地址当前是活动物理副本，拒绝改为暂存状态");
        }
        if (StringUtils.hasText(existing.getStorageKey())
                && !existing.getStorageKey().equals(storageKey)) {
            throw new IllegalStateException("目标地址对应的对象键与历史记录不一致");
        }
        if (StringUtils.hasText(existing.getContentHash())
                && !hashEquals(existing.getContentHash(), contentHash)) {
            throw new IllegalStateException("目标地址对应的历史副本哈希与完整回读结果不一致");
        }

        var update = Wrappers.<ResourceLocation>lambdaUpdate()
                .eq(ResourceLocation::getId, existing.getId())
                .eq(ResourceLocation::getResourceId, resource.getId())
                .eq(ResourceLocation::getStoreType, existing.getStoreType())
                .eq(ResourceLocation::getAccessPath, existing.getAccessPath())
                .eq(ResourceLocation::getStatus, existing.getStatus());
        if (StringUtils.hasText(existing.getStorageKey())) {
            update.eq(ResourceLocation::getStorageKey, existing.getStorageKey());
        } else {
            update.and(wrapper -> wrapper.isNull(ResourceLocation::getStorageKey)
                    .or()
                    .eq(ResourceLocation::getStorageKey, ""));
        }
        if (StringUtils.hasText(existing.getContentHash())) {
            update.eq(ResourceLocation::getContentHash, existing.getContentHash());
        } else {
            update.and(wrapper -> wrapper.isNull(ResourceLocation::getContentHash)
                    .or()
                    .eq(ResourceLocation::getContentHash, ""));
        }

        int updated = resourceLocationMapper.update(
                null,
                update.set(ResourceLocation::getStorageKey, storageKey)
                        .set(ResourceLocation::getContentHash, contentHash)
                        .set(ResourceLocation::getSize, size)
                        .set(ResourceLocation::getMimeType, mimeType)
                        .set(ResourceLocation::getStatus, ResourceLocationStatus.STAGED.name())
                        .set(ResourceLocation::getVerifiedAt, verifiedAt)
        );
        if (updated != 1) {
            throw new ConcurrentModificationException("目标物理副本登记期间发生并发修改");
        }
        return requireLocation(resource.getId(), existing.getId());
    }

    /**
     * 根据 ID 获取 location 记录（供外部服务校验物理文件存在性）。
     */
    public ResourceLocation getActiveLocationById(Long locationId) {
        if (locationId == null) return null;
        return resourceLocationMapper.selectById(locationId);
    }

    private ResourceLocation findLocation(String storeType, String accessPath) {
        return resourceLocationMapper.selectOne(
                Wrappers.<ResourceLocation>lambdaQuery()
                        .eq(ResourceLocation::getStoreType, storeType)
                        .eq(ResourceLocation::getAccessPath, accessPath)
                        .last("limit 1")
        );
    }

    private ResourceLocation registerLocation(Resource resource,
                                              String storeType,
                                              String storageKey,
                                              String accessPath,
                                              String contentHash,
                                              Long size,
                                              String mimeType,
                                              ResourceLocationStatus status,
                                              LocalDateTime verifiedAt) {
        String normalizedStoreType = normalizeStoreType(storeType);
        String normalizedHash = normalizeHash(contentHash);
        if (!StringUtils.hasText(accessPath)) {
            throw new IllegalArgumentException("物理副本访问地址不能为空");
        }
        ResourceLocation existing = findLocation(normalizedStoreType, accessPath);
        if (existing != null) {
            return adoptExistingLocation(
                    resource,
                    existing,
                    storageKey,
                    normalizedHash,
                    size,
                    mimeType,
                    status,
                    verifiedAt
            );
        }

        ResourceLocation location = new ResourceLocation();
        location.setResourceId(resource.getId());
        location.setStoreType(normalizedStoreType);
        location.setStorageKey(storageKey);
        location.setAccessPath(accessPath);
        location.setContentHash(normalizedHash);
        location.setSize(size);
        location.setMimeType(mimeType);
        location.setStatus(status.name());
        location.setVerifiedAt(verifiedAt);
        try {
            resourceLocationMapper.insert(location);
            return location;
        } catch (DuplicateKeyException e) {
            ResourceLocation concurrent = findLocation(normalizedStoreType, accessPath);
            if (concurrent == null) {
                throw new IllegalStateException("物理副本地址摘要冲突", e);
            }
            return adoptExistingLocation(
                    resource,
                    concurrent,
                    storageKey,
                    normalizedHash,
                    size,
                    mimeType,
                    status,
                    verifiedAt
            );
        }
    }

    private ResourceLocation adoptExistingLocation(Resource resource,
                                                   ResourceLocation existing,
                                                   String storageKey,
                                                   String contentHash,
                                                   Long size,
                                                   String mimeType,
                                                   ResourceLocationStatus targetStatus,
                                                   LocalDateTime verifiedAt) {
        if (!resource.getId().equals(existing.getResourceId())) {
            throw new IllegalStateException("物理副本地址已属于其他资源");
        }
        if (targetStatus != ResourceLocationStatus.ACTIVE) {
            throw new IllegalStateException("历史位置登记只允许建立活动副本");
        }
        if (ResourceLocationStatus.DELETED.name().equals(existing.getStatus())
                || ResourceLocationStatus.MISSING.name().equals(existing.getStatus())
                || ResourceLocationStatus.STALE.name().equals(existing.getStatus())
                || ResourceLocationStatus.DELETING.name().equals(existing.getStatus())
                || ResourceLocationStatus.DETACHED.name().equals(existing.getStatus())) {
            throw new IllegalStateException("已有物理副本记录不可重新作为活动源使用");
        }
        if (StringUtils.hasText(existing.getStorageKey())
                && StringUtils.hasText(storageKey)
                && !existing.getStorageKey().equals(storageKey)) {
            throw new IllegalStateException("物理副本对象键与历史记录不一致");
        }
        if (StringUtils.hasText(existing.getContentHash())
                && StringUtils.hasText(contentHash)
                && !hashEquals(existing.getContentHash(), contentHash)) {
            throw new IllegalStateException("物理副本内容哈希与历史记录不一致");
        }
        if (ResourceLocationStatus.ACTIVE.name().equals(existing.getStatus())) {
            return existing;
        }

        var update = Wrappers.<ResourceLocation>lambdaUpdate()
                .eq(ResourceLocation::getId, existing.getId())
                .eq(ResourceLocation::getResourceId, resource.getId())
                .eq(ResourceLocation::getStatus, existing.getStatus())
                .set(ResourceLocation::getStatus, ResourceLocationStatus.ACTIVE.name());
        if (!StringUtils.hasText(existing.getStorageKey()) && StringUtils.hasText(storageKey)) {
            update.set(ResourceLocation::getStorageKey, storageKey);
        }
        if (!StringUtils.hasText(existing.getContentHash()) && StringUtils.hasText(contentHash)) {
            update.set(ResourceLocation::getContentHash, contentHash);
        }
        if (existing.getSize() == null && size != null) {
            update.set(ResourceLocation::getSize, size);
        }
        if (!StringUtils.hasText(existing.getMimeType()) && StringUtils.hasText(mimeType)) {
            update.set(ResourceLocation::getMimeType, mimeType);
        }
        if (verifiedAt != null) {
            update.set(ResourceLocation::getVerifiedAt, verifiedAt);
        }
        if (resourceLocationMapper.update(null, update) != 1) {
            throw new ConcurrentModificationException("历史物理副本登记期间发生并发修改");
        }
        return requireLocation(resource.getId(), existing.getId());
    }

    private void appendNullableResourceEquality(
            com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Resource> update,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<Resource, ?> column,
            Object value) {
        if (value instanceof String text && !StringUtils.hasText(text)) {
            update.and(wrapper -> wrapper.isNull(column).or().eq(column, ""));
        } else if (value == null) {
            update.isNull(column);
        } else {
            update.eq(column, value);
        }
    }

    private void appendNullableLocationEquality(
            com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ResourceLocation> update,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<ResourceLocation, String> column,
            String value) {
        if (StringUtils.hasText(value)) {
            update.eq(column, value);
        } else {
            update.and(wrapper -> wrapper.isNull(column).or().eq(column, ""));
        }
    }

    private Resource requireResource(Integer resourceId) {
        Resource resource = resourceService.getById(resourceId);
        if (resource == null) {
            throw new IllegalArgumentException("资源不存在：" + resourceId);
        }
        return resource;
    }

    private void requireActiveContent(Resource resource, String message) {
        if (resource == null
                || !Boolean.TRUE.equals(resource.getStatus())
                || !ResourceContentState.isActive(resource.getContentState())) {
            throw new IllegalStateException(message);
        }
    }

    private boolean isPhysicalPath(String path) {
        return StringUtils.hasText(path) && !path.startsWith(MEDIA_PREFIX);
    }

    private String newPublicId() {
        return UUID.randomUUID().toString().replace("-", "").toLowerCase(Locale.ROOT);
    }

    private String normalizeStoreType(String storeType) {
        return StringUtils.hasText(storeType) ? storeType : "local";
    }

    private String requireHash(String hash, String message) {
        String normalized = normalizeHash(hash);
        if (normalized == null || !normalized.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String normalizeHash(String hash) {
        return StringUtils.hasText(hash) ? hash.toLowerCase(Locale.ROOT) : null;
    }

    private boolean hashEquals(String left, String right) {
        return StringUtils.hasText(left)
                && StringUtils.hasText(right)
                && left.equalsIgnoreCase(right);
    }

    private int normalizeVersion(Integer version) {
        return version == null ? 0 : version;
    }

    public record VerifiedUpload(
            Integer userId,
            String type,
            String storeType,
            String storageKey,
            String accessPath,
            String contentHash,
            long size,
            String mimeType,
            String originalName,
            Integer width,
            Integer height,
            LocalDateTime verifiedAt
    ) {
    }

    public record RegisteredUpload(
            Resource resource,
            ResourceLocation location,
            boolean reused
    ) {
        public String stablePath() {
            return resource == null ? null : resource.getPath();
        }
    }
}