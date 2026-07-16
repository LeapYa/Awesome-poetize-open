package com.ld.poetry.service;

import com.ld.poetry.controller.dto.ResourceLocationDeleteRequest;
import com.ld.poetry.controller.dto.ResourceLocationDeleteResult;
import com.ld.poetry.entity.Resource;
import com.ld.poetry.entity.ResourceLocation;
import com.ld.poetry.enums.ResourceLocationStatus;
import com.ld.poetry.utils.storage.FileStorageService;
import com.ld.poetry.utils.storage.StorageDeleteResult;
import com.ld.poetry.utils.storage.StorageResourceRef;
import com.ld.poetry.utils.storage.StorageSnapshot;
import com.ld.poetry.utils.storage.StorageVerificationResult;
import com.ld.poetry.utils.storage.StoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResourceLocationDeleteService {

    private final ResourceLocationService resourceLocationService;
    private final FileStorageService fileStorageService;
    private final ResourceStorageSnapshotService snapshotService;

    @Value("${resource.location-delete.lease-seconds:300}")
    private long leaseSeconds = 300;

    public ResourceLocationDeleteResult delete(ResourceLocationDeleteRequest request) {
        validate(request);
        ResourceLocation requestedLocation = resourceLocationService.requireLocation(
                request.resourceId(),
                request.locationId()
        );
        StoreService storeService = requireDeleteStore(requestedLocation);

        ResourceLocationService.LocationDeletionClaim claim =
                resourceLocationService.claimLocationDeletion(
                        request.resourceId(),
                        request.locationId(),
                        request.replacementLocationId()
                );
        return claim.claimed() ? executeClaim(storeService, claim) : existingState(claim);
    }

    public ResourceLocationDeleteResult deleteClaimed(
            ResourceLocationService.LocationDeletionClaim claim) {
        if (claim == null
                || !claim.claimed()
                || claim.resource() == null
                || claim.location() == null
                || claim.claimedAt() == null
                || !ResourceLocationStatus.DELETING.name().equals(claim.location().getStatus())) {
            throw new IllegalArgumentException("物理副本删除声明不完整");
        }
        return executeClaim(requireDeleteStore(claim.location()), claim);
    }

    public ResourceLocationDeleteResult resumeStale(Integer resourceId, Long locationId) {
        if (resourceId == null || locationId == null) {
            throw new IllegalArgumentException("资源ID和物理副本ID不能为空");
        }
        if (leaseSeconds <= 0) {
            throw new IllegalStateException("物理副本删除租约配置不合法");
        }
        ResourceLocation location = resourceLocationService.requireLocation(resourceId, locationId);
        StoreService storeService = requireDeleteStore(location);
        ResourceLocationService.LocationDeletionClaim claim =
                resourceLocationService.reclaimStaleLocationDeletion(
                        resourceId,
                        locationId,
                        LocalDateTime.now().minusSeconds(leaseSeconds)
                );
        return claim.claimed() ? executeClaim(storeService, claim) : existingState(claim);
    }

    private ResourceLocationDeleteResult executeClaim(
            StoreService storeService,
            ResourceLocationService.LocationDeletionClaim claim) {
        StorageResourceRef storageRef = storageRef(claim.resource(), claim.location());
        StorageDeleteResult storageResult;
        try {
            List<StorageDeleteResult> results = storeService.deleteFiles(List.of(storageRef));
            storageResult = results.size() == 1
                    ? results.getFirst()
                    : StorageDeleteResult.failed(storageRef, "存储平台未返回唯一的删除结果");
        } catch (RuntimeException e) {
            log.warn("物理副本删除调用异常，进入严格回读恢复: resourceId={}, locationId={}",
                    claim.resource().getId(), claim.location().getId(), e);
            return reconcileFailedDelete(storeService, claim, storageRef, e.getMessage());
        }

        if (!storageResult.success() && !storageResult.missing()) {
            return reconcileFailedDelete(storeService, claim, storageRef, storageResult.message());
        }

        resourceLocationService.completeLocationDeletion(
                claim.resource().getId(),
                claim.location().getId(),
                claim.claimedAt(),
                storageResult.missing()
        );
        return result(
                claim,
                storageResult.missing()
                        ? ResourceLocationStatus.MISSING.name()
                        : ResourceLocationStatus.DELETED.name(),
                storageResult.success(),
                true,
                storageResult.missing()
                        ? "物理副本已不存在，位置记录已标记缺失"
                        : "物理副本已删除"
        );
    }

    private ResourceLocationDeleteResult reconcileFailedDelete(
            StoreService storeService,
            ResourceLocationService.LocationDeletionClaim claim,
            StorageResourceRef storageRef,
            String failureMessage) {
        StorageVerificationResult verification;
        try {
            verification = storeService.verify(storageRef);
        } catch (RuntimeException e) {
            verification = StorageVerificationResult.unknown(e.getMessage());
        }
        if (verification == null) {
            verification = StorageVerificationResult.unknown("存储平台未返回校验结果");
        }
        if (verification.state() == StorageVerificationResult.State.MISSING) {
            resourceLocationService.completeLocationDeletion(
                    claim.resource().getId(),
                    claim.location().getId(),
                    claim.claimedAt(),
                    true
            );
            return result(
                    claim,
                    ResourceLocationStatus.MISSING.name(),
                    false,
                    true,
                    "删除结果不确定，但物理副本已明确不存在"
            );
        }

        if (canProveOriginalContent(storeService, storageRef)) {
            resourceLocationService.restoreLocationDeletion(
                    claim.resource().getId(),
                    claim.location().getId(),
                    claim.claimedAt(),
                    claim.originalStatus()
            );
            return result(
                    claim,
                    claim.originalStatus(),
                    false,
                    false,
                    StringUtils.hasText(failureMessage) ? failureMessage : "物理副本删除失败"
            );
        }

        return result(
                claim,
                ResourceLocationStatus.DELETING.name(),
                false,
                false,
                (StringUtils.hasText(failureMessage) ? failureMessage : "物理副本删除结果不确定")
                        + "；完整回读无法证明原副本仍然一致，保持阻塞等待恢复"
        );
    }

    private boolean canProveOriginalContent(StoreService storeService,
                                            StorageResourceRef storageRef) {
        if (!storeService.getCapability().readSupported()
                || !StringUtils.hasText(storageRef.hash())
                || storageRef.size() == null) {
            return false;
        }
        try (StorageSnapshot snapshot = snapshotService.capture(storeService, storageRef)) {
            return storageRef.hash().equalsIgnoreCase(snapshot.sha256())
                    && storageRef.size() == snapshot.size();
        } catch (IOException | RuntimeException e) {
            log.warn("物理副本删除失败后的完整回读证明失败: path={}", storageRef.path(), e);
            return false;
        }
    }

    private StoreService requireDeleteStore(ResourceLocation location) {
        StoreService storeService = fileStorageService.getFileStorageByStoreType(location.getStoreType());
        if (!storeService.getCapability().enabled()
                || !storeService.getCapability().deleteSupported()) {
            throw new IllegalStateException("当前存储平台不支持物理副本删除");
        }
        return storeService;
    }

    private void validate(ResourceLocationDeleteRequest request) {
        if (request == null || request.resourceId() == null || request.locationId() == null) {
            throw new IllegalArgumentException("资源ID和物理副本ID不能为空");
        }
        if (request.locationId().equals(request.replacementLocationId())) {
            throw new IllegalArgumentException("待删除副本不能同时作为替代副本");
        }
    }

    private ResourceLocationDeleteResult existingState(
            ResourceLocationService.LocationDeletionClaim claim) {
        String status = claim.location().getStatus();
        boolean terminal = ResourceLocationStatus.DELETED.name().equals(status)
                || ResourceLocationStatus.MISSING.name().equals(status);
        String message = switch (ResourceLocationStatus.valueOf(status)) {
            case DELETED -> "物理副本此前已删除";
            case MISSING -> "物理副本此前已标记缺失";
            case DELETING -> "物理副本正在由其他请求清理";
            default -> "物理副本状态未发生变化";
        };
        return result(claim, status, false, terminal, message);
    }

    private StorageResourceRef storageRef(Resource resource, ResourceLocation location) {
        return new StorageResourceRef(
                resource.getId(),
                location.getAccessPath(),
                location.getStorageKey(),
                resource.getOriginalName(),
                location.getSize(),
                location.getContentHash(),
                location.getMimeType()
        );
    }

    private ResourceLocationDeleteResult result(
            ResourceLocationService.LocationDeletionClaim claim,
            String status,
            boolean physicalDeleted,
            boolean recordMarkedRemoved,
            String message) {
        return new ResourceLocationDeleteResult(
                claim.resource().getId(),
                claim.location().getId(),
                claim.resource().getActiveLocationId(),
                status,
                physicalDeleted,
                recordMarkedRemoved,
                message == null ? "" : message
        );
    }
}