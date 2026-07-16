package com.ld.poetry.service;

import com.ld.poetry.controller.dto.ResourceBatchDeleteRequest;
import com.ld.poetry.controller.dto.ResourceBatchDeleteResult;
import com.ld.poetry.entity.Resource;
import com.ld.poetry.entity.ResourceLocation;
import com.ld.poetry.enums.ResourceContentState;
import com.ld.poetry.enums.ResourceLocationStatus;
import com.ld.poetry.utils.storage.FileStorageService;
import com.ld.poetry.utils.storage.StorageCapability;
import com.ld.poetry.utils.storage.StorageResourceRef;
import com.ld.poetry.utils.storage.StorageVerificationResult;
import com.ld.poetry.utils.storage.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ResourceBatchDeleteService {

    private static final int MAX_BATCH_SIZE = 500;

    private final ResourceDeletionStateService deletionStateService;
    private final ResourceLocationDeleteService locationDeleteService;
    private final ResourceReferenceService referenceService;
    private final FileStorageService fileStorageService;

    public ResourceBatchDeleteResult preview(ResourceBatchDeleteRequest request) {
        List<Inspection> inspections = inspect(request);
        List<ResourceBatchDeleteResult.Item> items = inspections.stream()
                .map(inspection -> toResultItem(inspection, false, false, inspection.message()))
                .toList();
        int ready = (int) inspections.stream().filter(Inspection::ready).count();
        return new ResourceBatchDeleteResult(
                inspections.size(), ready, 0, inspections.size() - ready, 0, items
        );
    }

    public ResourceBatchDeleteResult delete(ResourceBatchDeleteRequest request) {
        List<Inspection> inspections = inspect(request);
        List<ResourceBatchDeleteResult.Item> items = new ArrayList<>(inspections.size());
        int deleted = 0;
        int blocked = 0;
        int failed = 0;

        for (Inspection inspection : inspections) {
            if (!inspection.ready()) {
                blocked++;
                items.add(toResultItem(inspection, false, false, inspection.message()));
                continue;
            }

            Execution execution = execute(inspection);
            if (execution.recordDeleted()) {
                deleted++;
            } else {
                failed++;
            }
            items.add(resultItem(
                    inspection,
                    execution.status(),
                    execution.physicalDeleted(),
                    execution.recordDeleted(),
                    execution.message()
            ));
        }

        int ready = (int) inspections.stream().filter(Inspection::ready).count();
        return new ResourceBatchDeleteResult(
                inspections.size(), ready, deleted, blocked, failed, items
        );
    }

    private Execution execute(Inspection inspection) {
        boolean physicalDeleted = false;
        List<String> messages = new ArrayList<>();
        try {
            ResourceDeletionStateService.LogicalDeletionClaim claim = deletionStateService.claim(
                    new ResourceDeletionStateService.LogicalDeletionPlan(
                            inspection.resource().getId(),
                            inspection.resource().getPath(),
                            inspection.forceReferenced(),
                            inspection.dispositions()
                    )
            );

            for (ResourceLocationService.LocationDeletionClaim locationClaim : claim.claimedLocations()) {
                var result = locationDeleteService.deleteClaimed(locationClaim);
                physicalDeleted = physicalDeleted || result.physicalDeleted();
                if (StringUtils.hasText(result.message())) {
                    messages.add(result.message());
                }
            }
            for (Long locationId : claim.inProgressLocationIds()) {
                var result = locationDeleteService.resumeStale(
                        inspection.resource().getId(),
                        locationId
                );
                physicalDeleted = physicalDeleted || result.physicalDeleted();
                if (StringUtils.hasText(result.message())) {
                    messages.add(result.message());
                }
            }

            boolean finalized = deletionStateService.finalizeDeletion(
                    inspection.resource().getId(),
                    inspection.resource().getPath(),
                    inspection.forceReferenced()
            );
            if (finalized) {
                return new Execution(
                        "DELETED",
                        physicalDeleted,
                        true,
                        messages.isEmpty() ? "逻辑资源及全部物理副本已处理" : String.join("；", messages)
                );
            }
            boolean inProgress = messages.stream().anyMatch(message -> message.contains("正在")
                    || message.contains("等待恢复")
                    || message.contains("租约"));
            return new Execution(
                    inProgress ? "DELETING" : "FAILED",
                    physicalDeleted,
                    false,
                    messages.isEmpty()
                            ? "仍有物理副本未达到删除终态，请重试"
                            : String.join("；", messages)
            );
        } catch (RuntimeException e) {
            return new Execution(
                    "FAILED",
                    physicalDeleted,
                    false,
                    StringUtils.hasText(e.getMessage()) ? e.getMessage() : "逻辑资源删除失败"
            );
        }
    }

    private List<Inspection> inspect(ResourceBatchDeleteRequest request) {
        List<ResourceBatchDeleteRequest.Target> targets = validateTargets(request);
        Map<Integer, ResourceBatchDeleteRequest.Target> uniqueTargets = new LinkedHashMap<>();
        targets.forEach(target -> uniqueTargets.putIfAbsent(target.resourceId(), target));

        List<Inspection> inspections = new ArrayList<>(uniqueTargets.size());
        for (ResourceBatchDeleteRequest.Target target : uniqueTargets.values()) {
            Resource resource;
            try {
                resource = deletionStateService.requireResource(target.resourceId());
            } catch (IllegalArgumentException e) {
                inspections.add(Inspection.changed(target.resourceId(), target.expectedPath(), e.getMessage()));
                continue;
            }
            if (!target.expectedPath().equals(resource.getPath())) {
                inspections.add(Inspection.changed(resource.getId(), resource.getPath(), "资源路径已变化"));
                continue;
            }

            boolean pending = !Boolean.TRUE.equals(resource.getStatus())
                    && ResourceContentState.DELETION_PENDING.name().equals(resource.getContentState());
            if (!pending && (!Boolean.TRUE.equals(resource.getStatus())
                    || !ResourceContentState.isActive(resource.getContentState()))) {
                inspections.add(Inspection.blocked(
                        resource,
                        "STATE_BLOCKED",
                        0,
                        false,
                        StorageVerificationResult.State.UNKNOWN.name(),
                        "资源当前状态不允许删除"
                ));
                continue;
            }

            int referenceCount = countReferences(resource);
            if (referenceCount > 0 && !request.forceReferenced()) {
                inspections.add(Inspection.blocked(
                        resource,
                        "REFERENCED",
                        referenceCount,
                        false,
                        StorageVerificationResult.State.UNKNOWN.name(),
                        "稳定地址或历史别名仍被 " + referenceCount + " 处业务数据引用"
                ));
                continue;
            }

            List<ResourceLocation> locations = deletionStateService.listLocations(resource.getId());
            if (locations.isEmpty()) {
                inspections.add(Inspection.blocked(
                        resource,
                        "UNMANAGED",
                        referenceCount,
                        false,
                        StorageVerificationResult.State.UNKNOWN.name(),
                        "逻辑资源尚未登记物理副本，不能按路径猜测删除"
                ));
                continue;
            }
            inspections.add(inspectLocations(resource, locations, referenceCount, request));
        }
        return inspections;
    }

    private Inspection inspectLocations(Resource resource,
                                        List<ResourceLocation> locations,
                                        int referenceCount,
                                        ResourceBatchDeleteRequest request) {
        List<ResourceDeletionStateService.LocationDeletionDisposition> dispositions = new ArrayList<>();
        Set<String> stores = new LinkedHashSet<>();
        boolean allDeleteSupported = true;
        StorageVerificationResult.State aggregateState = StorageVerificationResult.State.AVAILABLE;
        String blockedStatus = null;
        String blockedMessage = null;

        for (ResourceLocation location : locations) {
            ResourceLocationStatus status = requireStatus(location);
            if (isTerminal(status)) {
                continue;
            }
            stores.add(location.getStoreType());

            StoreService storeService;
            StorageCapability capability;
            try {
                storeService = fileStorageService.getFileStorageByStoreType(location.getStoreType());
                capability = storeService.getCapability();
            } catch (RuntimeException e) {
                storeService = null;
                capability = null;
            }

            boolean deletable = capability != null
                    && capability.enabled()
                    && capability.deleteSupported();
            if (!deletable) {
                allDeleteSupported = false;
                aggregateState = StorageVerificationResult.State.UNKNOWN;
                if (request.removeUnsupportedRecords()) {
                    dispositions.add(disposition(
                            location,
                            ResourceDeletionStateService.LocationDisposition.DETACH
                    ));
                } else if (blockedStatus == null) {
                    blockedStatus = "UNSUPPORTED";
                    blockedMessage = "副本 " + location.getId() + " 的存储平台不支持服务端删除";
                }
                continue;
            }

            StorageVerificationResult verification;
            try {
                verification = storeService.verify(storageRef(resource, location));
            } catch (RuntimeException e) {
                verification = StorageVerificationResult.unknown(e.getMessage());
            }
            aggregateState = aggregate(aggregateState, verification.state());
            if (verification.state() == StorageVerificationResult.State.MISSING) {
                if (request.removeMissingRecords()) {
                    dispositions.add(disposition(
                            location,
                            ResourceDeletionStateService.LocationDisposition.MARK_MISSING
                    ));
                } else if (blockedStatus == null) {
                    blockedStatus = "MISSING";
                    blockedMessage = "副本 " + location.getId() + " 已缺失，需要明确确认移除缺失记录";
                }
            } else {
                dispositions.add(disposition(
                        location,
                        ResourceDeletionStateService.LocationDisposition.DELETE
                ));
            }
        }

        String storeType = stores.isEmpty()
                ? resource.getStoreType()
                : stores.size() == 1 ? stores.iterator().next() : "MIXED";
        if (blockedStatus != null) {
            return Inspection.blocked(
                    resource,
                    storeType,
                    blockedStatus,
                    referenceCount,
                    allDeleteSupported,
                    aggregateState.name(),
                    blockedMessage
            );
        }
        return Inspection.ready(
                resource,
                storeType,
                referenceCount,
                allDeleteSupported,
                aggregateState.name(),
                request.forceReferenced(),
                dispositions,
                "可按副本状态机安全删除"
        );
    }

    private int countReferences(Resource resource) {
        Set<String> identities = new LinkedHashSet<>();
        identities.add(resource.getPath());
        identities.addAll(deletionStateService.listActiveAliases(resource.getId()));
        return identities.stream()
                .filter(StringUtils::hasText)
                .mapToInt(referenceService::countReferences)
                .sum();
    }

    private List<ResourceBatchDeleteRequest.Target> validateTargets(ResourceBatchDeleteRequest request) {
        if (request == null || CollectionUtils.isEmpty(request.targets())) {
            throw new IllegalArgumentException("请选择要删除的资源");
        }
        if (request.targets().size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("单次最多删除 " + MAX_BATCH_SIZE + " 个资源");
        }
        for (ResourceBatchDeleteRequest.Target target : request.targets()) {
            if (target == null || target.resourceId() == null || !StringUtils.hasText(target.expectedPath())) {
                throw new IllegalArgumentException("资源ID和期望路径不能为空");
            }
        }
        return request.targets();
    }

    private ResourceDeletionStateService.LocationDeletionDisposition disposition(
            ResourceLocation location,
            ResourceDeletionStateService.LocationDisposition disposition) {
        return new ResourceDeletionStateService.LocationDeletionDisposition(
                location.getId(),
                disposition
        );
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

    private ResourceLocationStatus requireStatus(ResourceLocation location) {
        try {
            return ResourceLocationStatus.valueOf(location.getStatus());
        } catch (RuntimeException e) {
            throw new IllegalStateException("物理副本状态不合法：" + location.getStatus(), e);
        }
    }

    private boolean isTerminal(ResourceLocationStatus status) {
        return status == ResourceLocationStatus.DELETED
                || status == ResourceLocationStatus.MISSING
                || status == ResourceLocationStatus.DETACHED;
    }

    private StorageVerificationResult.State aggregate(StorageVerificationResult.State current,
                                                      StorageVerificationResult.State next) {
        if (current == StorageVerificationResult.State.MISSING
                || next == StorageVerificationResult.State.MISSING) {
            return StorageVerificationResult.State.MISSING;
        }
        if (current == StorageVerificationResult.State.UNKNOWN
                || next == StorageVerificationResult.State.UNKNOWN) {
            return StorageVerificationResult.State.UNKNOWN;
        }
        return StorageVerificationResult.State.AVAILABLE;
    }

    private ResourceBatchDeleteResult.Item toResultItem(Inspection inspection,
                                                         boolean physicalDeleted,
                                                         boolean recordDeleted,
                                                         String message) {
        return resultItem(inspection, inspection.status(), physicalDeleted, recordDeleted, message);
    }

    private ResourceBatchDeleteResult.Item resultItem(Inspection inspection,
                                                       String status,
                                                       boolean physicalDeleted,
                                                       boolean recordDeleted,
                                                       String message) {
        Resource resource = inspection.resource();
        return new ResourceBatchDeleteResult.Item(
                resource == null ? inspection.resourceId() : resource.getId(),
                resource == null ? inspection.path() : resource.getPath(),
                inspection.storeType(),
                status,
                inspection.referenceCount(),
                inspection.deleteSupported(),
                inspection.verificationState(),
                physicalDeleted,
                recordDeleted,
                message == null ? "" : message
        );
    }

    private record Inspection(
            Resource resource,
            Integer resourceId,
            String path,
            String storeType,
            String status,
            int referenceCount,
            boolean deleteSupported,
            String verificationState,
            boolean ready,
            boolean forceReferenced,
            List<ResourceDeletionStateService.LocationDeletionDisposition> dispositions,
            String message
    ) {
        private static Inspection changed(Integer resourceId, String path, String message) {
            return new Inspection(
                    null,
                    resourceId,
                    path,
                    null,
                    "CHANGED",
                    0,
                    false,
                    StorageVerificationResult.State.UNKNOWN.name(),
                    false,
                    false,
                    List.of(),
                    message
            );
        }

        private static Inspection blocked(Resource resource,
                                          String status,
                                          int referenceCount,
                                          boolean deleteSupported,
                                          String verificationState,
                                          String message) {
            return blocked(
                    resource,
                    resource.getStoreType(),
                    status,
                    referenceCount,
                    deleteSupported,
                    verificationState,
                    message
            );
        }

        private static Inspection blocked(Resource resource,
                                          String storeType,
                                          String status,
                                          int referenceCount,
                                          boolean deleteSupported,
                                          String verificationState,
                                          String message) {
            return new Inspection(
                    resource,
                    resource.getId(),
                    resource.getPath(),
                    storeType,
                    status,
                    referenceCount,
                    deleteSupported,
                    verificationState,
                    false,
                    false,
                    List.of(),
                    message
            );
        }

        private static Inspection ready(
                Resource resource,
                String storeType,
                int referenceCount,
                boolean deleteSupported,
                String verificationState,
                boolean forceReferenced,
                List<ResourceDeletionStateService.LocationDeletionDisposition> dispositions,
                String message) {
            return new Inspection(
                    resource,
                    resource.getId(),
                    resource.getPath(),
                    storeType,
                    "READY",
                    referenceCount,
                    deleteSupported,
                    verificationState,
                    true,
                    forceReferenced,
                    List.copyOf(dispositions),
                    message
            );
        }
    }

    private record Execution(
            String status,
            boolean physicalDeleted,
            boolean recordDeleted,
            String message
    ) {
    }
}