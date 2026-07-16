package com.ld.poetry.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ld.poetry.controller.dto.ResourceLocationDeleteRequest;
import com.ld.poetry.controller.dto.ResourceLocationDeleteResult;
import com.ld.poetry.controller.dto.ResourceMigrationCleanupResult;
import com.ld.poetry.controller.dto.ResourceMigrationPreview;
import com.ld.poetry.controller.dto.ResourceMigrationRequest;
import com.ld.poetry.controller.dto.ResourceMigrationTaskView;
import com.ld.poetry.dao.ResourceMigrationItemMapper;
import com.ld.poetry.entity.Resource;
import com.ld.poetry.entity.ResourceLocation;
import com.ld.poetry.entity.ResourceMigrationItem;
import com.ld.poetry.entity.ResourceMigrationTask;
import com.ld.poetry.enums.ResourceContentState;
import com.ld.poetry.enums.ResourceLocationStatus;
import com.ld.poetry.enums.ResourceMigrationItemStatus;
import com.ld.poetry.enums.ResourceMigrationTaskStatus;
import com.ld.poetry.utils.storage.FileStorageService;
import com.ld.poetry.utils.storage.PathMultipartFile;
import com.ld.poetry.utils.storage.StorageCapability;
import com.ld.poetry.utils.storage.StorageResourceRef;
import com.ld.poetry.utils.storage.StorageSnapshot;
import com.ld.poetry.utils.storage.StorageVerificationResult;
import com.ld.poetry.utils.storage.StoreService;
import com.ld.poetry.vo.FileVO;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceMigrationService {

    private static final int TASK_ITEM_VIEW_LIMIT = 500;
    private static final int MAX_ITEM_TRANSITIONS = 16;
    private static final Set<String> RESUMABLE_ITEM_STATUSES = Set.of(
            ResourceMigrationItemStatus.PENDING.name(),
            ResourceMigrationItemStatus.SNAPSHOTTING.name(),
            ResourceMigrationItemStatus.SNAPSHOT_READY.name(),
            ResourceMigrationItemStatus.WRITING.name(),
            ResourceMigrationItemStatus.TARGET_WRITTEN.name(),
            ResourceMigrationItemStatus.VERIFYING.name(),
            ResourceMigrationItemStatus.VERIFIED.name(),
            ResourceMigrationItemStatus.SWITCHED.name(),
            ResourceMigrationItemStatus.UPLOADING.name(),
            ResourceMigrationItemStatus.UPLOADED.name()
    );

    private final ResourceMigrationCandidateService candidateService;
    private final ResourceMigrationTaskStore taskStore;
    private final ResourceMigrationItemMapper itemMapper;
    private final ResourceService resourceService;
    private final FileStorageService fileStorageService;
    private final ResourceStorageSnapshotService snapshotService;
    private final ResourceLocationService resourceLocationService;
    private final ResourceLocationDeleteService resourceLocationDeleteService;
    private final ResourceMigrationSwitchService switchService;
    private final ResourceMigrationCacheService cacheService;

    private final ExecutorService executor = Executors.newFixedThreadPool(
            2,
            Thread.ofVirtual().name("resource-migration-", 0).factory()
    );
    private final Set<String> submittedTasks = ConcurrentHashMap.newKeySet();

    @PostConstruct
    public void recoverTasks() {
        try {
            taskStore.recoverPendingTaskIds().forEach(this::submit);
        } catch (Exception e) {
            log.info("资源迁移表尚未就绪或恢复任务失败，将等待数据库升级后由管理员重试: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    public List<StorageCapability> listTargetCapabilities() {
        return fileStorageService.listMigrationTargets();
    }

    public ResourceMigrationPreview preview(ResourceMigrationRequest request) {
        return candidateService.preview(request);
    }

    public ResourceMigrationTask create(ResourceMigrationRequest request, Integer createdBy) {
        if (request != null && Boolean.FALSE.equals(request.keepSource())) {
            throw new IllegalArgumentException("迁移任务必须保留源文件，任务完成后可单独清理");
        }
        List<ResourceMigrationCandidate> candidates = candidateService.resolveCandidates(request);
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("当前范围没有可迁移的资源");
        }
        if (candidates.stream().noneMatch(ResourceMigrationCandidate::eligible)) {
            throw new IllegalArgumentException("当前范围没有符合严格迁移条件的资源");
        }
        ResourceMigrationTask task = taskStore.createTask(request, createdBy, candidates);
        submit(task.getTaskId());
        return task;
    }

    public ResourceMigrationTaskView getTask(String taskId) {
        ResourceMigrationTask task = taskStore.refreshProgress(taskId);
        List<ResourceMigrationItem> items = itemMapper.selectList(
                Wrappers.<ResourceMigrationItem>lambdaQuery()
                        .eq(ResourceMigrationItem::getTaskId, taskId)
                        .orderByAsc(ResourceMigrationItem::getStatus)
                        .orderByAsc(ResourceMigrationItem::getId)
                        .last("limit " + TASK_ITEM_VIEW_LIMIT)
        );
        return new ResourceMigrationTaskView(
                task,
                items,
                task.getTotalCount() != null && task.getTotalCount() > items.size()
        );
    }

    public boolean cancel(String taskId) {
        return taskStore.cancelTask(taskId);
    }

    public ResourceMigrationTask retry(String taskId) {
        ResourceMigrationTask task = taskStore.prepareRetry(taskId);
        submit(taskId);
        return task;
    }

    public ResourceMigrationCleanupResult cleanupSources(String taskId) {
        ResourceMigrationTask task = taskStore.findTask(taskId);
        ResourceMigrationTaskStatus status = ResourceMigrationTaskStatus.valueOf(task.getStatus());
        if (!status.isTerminal() || status == ResourceMigrationTaskStatus.CANCELLED) {
            throw new IllegalArgumentException("任务完成后才能清理源文件");
        }

        List<ResourceMigrationItem> items = itemMapper.selectList(
                Wrappers.<ResourceMigrationItem>lambdaQuery()
                        .eq(ResourceMigrationItem::getTaskId, taskId)
                        .eq(ResourceMigrationItem::getStatus, ResourceMigrationItemStatus.SUCCESS.name())
                        .eq(ResourceMigrationItem::getSourceDeleted, false)
        );
        int cleaned = 0;
        int skipped = 0;
        int failed = 0;
        for (ResourceMigrationItem item : items) {
            try {
                CleanupOutcome outcome = cleanupSource(item);
                if (outcome == CleanupOutcome.CLEANED) {
                    cleaned++;
                } else if (outcome == CleanupOutcome.MISSING) {
                    skipped++;
                } else {
                    failed++;
                }
            } catch (Exception e) {
                log.warn("迁移源副本清理失败: itemId={}, err={}", item.getId(), e.getMessage());
                failed++;
            }
        }
        return new ResourceMigrationCleanupResult(items.size(), cleaned, skipped, failed);
    }

    private void submit(String taskId) {
        if (!submittedTasks.add(taskId)) {
            return;
        }
        executor.execute(() -> {
            try {
                runTask(taskId);
            } finally {
                submittedTasks.remove(taskId);
            }
        });
    }

    private void runTask(String taskId) {
        boolean switchedAnyLocation = false;
        try {
            ResourceMigrationTask task = taskStore.findTask(taskId);
            if (!taskStore.markRunning(task)) {
                return;
            }
            List<ResourceMigrationItem> items = itemMapper.selectList(
                    Wrappers.<ResourceMigrationItem>lambdaQuery()
                            .eq(ResourceMigrationItem::getTaskId, taskId)
                            .in(ResourceMigrationItem::getStatus, RESUMABLE_ITEM_STATUSES)
                            .orderByAsc(ResourceMigrationItem::getId)
            );

            for (ResourceMigrationItem item : items) {
                ResourceMigrationTask latest = taskStore.findTask(taskId);
                if (ResourceMigrationTaskStatus.CANCELLED.name().equals(latest.getStatus())) {
                    taskStore.finishTask(taskId, true, null);
                    return;
                }
                if (processItem(item)) {
                    switchedAnyLocation = true;
                }
                taskStore.refreshProgress(taskId);
            }
            taskStore.finishTask(taskId, false, null);
        } catch (Exception e) {
            log.error("资源迁移任务执行失败: taskId={}", taskId, e);
            try {
                taskStore.finishTask(taskId, false, e.getMessage());
            } catch (Exception finishError) {
                log.error("资源迁移任务状态收尾失败: taskId={}", taskId, finishError);
            }
        } finally {
            if (switchedAnyLocation) {
                cacheService.invalidateAfterMigration();
            }
        }
    }

    private boolean processItem(ResourceMigrationItem originalItem) {
        ResourceMigrationItem item = itemMapper.selectById(originalItem.getId());
        boolean switchedLocation = false;
        try {
            for (int transition = 0; transition < MAX_ITEM_TRANSITIONS; transition++) {
                ResourceMigrationItemStatus status = ResourceMigrationItemStatus.valueOf(item.getStatus());
                switch (status) {
                    case PENDING -> captureAndWrite(item);
                    case SNAPSHOTTING, SNAPSHOT_READY -> taskStore.resetSnapshot(item);
                    case WRITING, UPLOADING -> taskStore.resumeWriteAttempt(item);
                    case TARGET_WRITTEN -> verifyTarget(item);
                    case VERIFYING, UPLOADED -> taskStore.resetVerification(item);
                    case VERIFIED -> {
                        switchService.switchToTarget(item);
                        switchedLocation = true;
                    }
                    case SWITCHED -> {
                        taskStore.markSuccess(item, false);
                        return true;
                    }
                    case SUCCESS, SKIPPED, SOURCE_CHANGED, FAILED -> {
                        return switchedLocation;
                    }
                }
                item = requireItem(item.getId());
            }
            throw new IllegalStateException("迁移条目状态转换次数超过安全上限");
        } catch (ResourceMigrationSourceChangedException e) {
            taskStore.markSourceChanged(requireItemOrFallback(item), e.getMessage());
            return switchedLocation;
        } catch (Exception e) {
            taskStore.markFailed(requireItemOrFallback(item), e.getMessage());
            return switchedLocation;
        }
    }

    private void captureAndWrite(ResourceMigrationItem item) throws Exception {
        if (!taskStore.markSnapshotting(item)) {
            return;
        }
        ResourceMigrationItem snapshotting = requireItem(item.getId());
        Resource resource = requireFrozenActiveResource(snapshotting);
        StoreService sourceService = fileStorageService.getFileStorageByStoreType(snapshotting.getSourceStoreType());
        StorageResourceRef sourceRef = sourceRef(snapshotting, resource);

        try (StorageSnapshot snapshot = snapshotService.capture(sourceService, sourceRef)) {
            taskStore.acceptSourceSnapshot(
                    snapshotting,
                    snapshot.sha256(),
                    snapshot.size(),
                    snapshot.contentType()
            );
            ResourceMigrationItem snapshotReady = requireItem(item.getId());
            writeTarget(snapshotReady, resource, snapshot);
        }
    }

    private void writeTarget(ResourceMigrationItem item,
                             Resource resource,
                             StorageSnapshot sourceSnapshot) throws Exception {
        Resource latestResource = requireFrozenActiveResource(item);
        if (StringUtils.hasText(item.getSourceHash())
                && !hashEquals(latestResource.getResourceHash(), item.getSourceHash())) {
            throw new ResourceMigrationSourceChangedException("资源内容哈希在目标写入前发生变化");
        }
        StoreService targetService = fileStorageService.getFileStorageByStoreType(item.getTargetStoreType());
        StorageCapability targetCapability = targetService.getCapability();
        if (!targetCapability.enabled()
                || !targetCapability.uploadSupported()
                || !targetCapability.readSupported()
                || !targetCapability.verifySupported()) {
            throw new IllegalStateException("目标存储不再满足严格迁移能力要求");
        }
        if (!targetCapability.supports(sourceSnapshot.contentType(), sourceSnapshot.size())) {
            throw new IllegalStateException("源快照的实际大小或MIME类型不受目标存储支持");
        }

        if (targetService.supportsDeterministicWrite()) {
            writeTargetDeterministic(item, resource, sourceSnapshot, targetService);
        } else {
            writeTargetByReceipt(item, resource, sourceSnapshot, targetService);
        }
    }

    /**
     * 确定性写入路径：本地/七牛等支持指定对象键的存储。
     * 写入前可重建目标引用，支持已有目标幂等检查和中断安全恢复。
     */
    private void writeTargetDeterministic(ResourceMigrationItem item,
                                          Resource resource,
                                          StorageSnapshot sourceSnapshot,
                                          StoreService targetService) throws Exception {
        String targetKey = buildTargetKey(resource, sourceSnapshot.sha256());
        String targetPath = targetService.resolveAccessPath(targetKey);
        if (!StringUtils.hasText(targetPath)) {
            throw new IllegalStateException("目标存储无法重建确定性访问地址");
        }
        if (targetPath.length() > 2048 || targetKey.length() > 512) {
            throw new IllegalStateException("目标物理引用超过数据库长度限制");
        }
        if (!taskStore.markWriting(item, targetPath, targetKey)) {
            return;
        }

        ResourceMigrationItem writing = requireItem(item.getId());
        StorageResourceRef targetRef = targetRef(writing, resource);
        ExistingTarget existing = inspectExistingTarget(targetService, targetRef, sourceSnapshot.sha256());
        if (existing == ExistingTarget.MATCHED) {
            taskStore.markTargetWritten(writing, targetPath, targetKey, false);
            return;
        }

        FileVO request = new FileVO();
        request.setFile(new PathMultipartFile(
                "file",
                sourceSnapshot.originalName(),
                sourceSnapshot.contentType(),
                sourceSnapshot.path()
        ));
        request.setType(resource.getType());
        request.setStoreType(item.getTargetStoreType());
        request.setOriginalName(sourceSnapshot.originalName());
        request.setRelativePath(targetKey);
        request.setResourceHash(sourceSnapshot.sha256());
        request.setCreateOnly(true);

        FileVO result;
        try {
            result = targetService.saveFile(request);
        } catch (RuntimeException uploadError) {
            ExistingTarget afterFailure = inspectExistingTarget(
                    targetService,
                    targetRef,
                    sourceSnapshot.sha256()
            );
            if (afterFailure == ExistingTarget.MATCHED) {
                taskStore.markTargetWritten(writing, targetPath, targetKey, false);
                return;
            }
            throw uploadError;
        }

        validateDeterministicWriteResult(result, targetPath, targetKey);
        taskStore.markTargetWritten(writing, targetPath, targetKey, true);
    }

    /**
     * 回执型写入路径：兰空/EasyImage 等图床类存储，API 不允许调用者指定对象键。
     * 写入前无法预知目标地址，上传后以服务端返回的物理地址和对象键为准，
     * 随后复用统一的完整回读验证流程（UPLOADED → TARGET_WRITTEN → VERIFYING）。
     * 代价：写入前无法做已有目标幂等检查，中断在"上传成功未记录键"窗口会产生孤儿副本。
     */
    private void writeTargetByReceipt(ResourceMigrationItem item,
                                      Resource resource,
                                      StorageSnapshot sourceSnapshot,
                                      StoreService targetService) throws Exception {
        if (!taskStore.markUploading(item)) {
            return;
        }

        FileVO request = new FileVO();
        request.setFile(new PathMultipartFile(
                "file",
                sourceSnapshot.originalName(),
                sourceSnapshot.contentType(),
                sourceSnapshot.path()
        ));
        request.setType(resource.getType());
        request.setStoreType(item.getTargetStoreType());
        request.setOriginalName(sourceSnapshot.originalName());
        request.setResourceHash(sourceSnapshot.sha256());
        request.setCreateOnly(true);

        FileVO result = targetService.saveFile(request);

        if (result == null
                || !StringUtils.hasText(result.getVisitPath())
                || !StringUtils.hasText(result.getStorageKey())) {
            throw new IllegalStateException("目标存储未返回有效的物理地址或对象键");
        }
        String targetPath = result.getVisitPath();
        String targetKey = result.getStorageKey();
        if (targetPath.length() > 2048 || targetKey.length() > 512) {
            throw new IllegalStateException("目标物理引用超过数据库长度限制");
        }

        ResourceMigrationItem uploading = requireItem(item.getId());
        taskStore.markUploaded(uploading, targetPath, targetKey);
    }

    private ExistingTarget inspectExistingTarget(StoreService targetService,
                                                  StorageResourceRef targetRef,
                                                  String expectedHash) {
        StorageVerificationResult metadata = targetService.verify(targetRef);
        if (metadata.state() == StorageVerificationResult.State.MISSING) {
            return ExistingTarget.MISSING;
        }
        try (StorageSnapshot existing = snapshotService.capture(targetService, targetRef)) {
            if (!hashEquals(expectedHash, existing.sha256())) {
                throw new IllegalStateException("确定性目标键已存在不同内容，拒绝覆盖");
            }
            return ExistingTarget.MATCHED;
        } catch (RuntimeException e) {
            if (metadata.state() == StorageVerificationResult.State.UNKNOWN) {
                throw new IllegalStateException("无法确认确定性目标是否存在，拒绝盲目覆盖: " + e.getMessage(), e);
            }
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("已有目标副本关闭失败: " + e.getMessage(), e);
        }
    }

    private void verifyTarget(ResourceMigrationItem item) throws Exception {
        requireHash(item.getSourceHash(), "迁移条目缺少源快照SHA-256");
        Resource resource = requireFrozenActiveResource(item);
        if (!hashEquals(resource.getResourceHash(), item.getSourceHash())) {
            throw new ResourceMigrationSourceChangedException("资源内容哈希在目标复验前发生变化");
        }
        StoreService targetService = fileStorageService.getFileStorageByStoreType(item.getTargetStoreType());
        StorageResourceRef targetRef = targetRef(item, resource);
        StorageVerificationResult metadata = targetService.verify(targetRef);
        if (metadata.state() == StorageVerificationResult.State.MISSING) {
            taskStore.resetMissingTarget(item);
            return;
        }
        if (!taskStore.markVerifying(item)) {
            return;
        }

        ResourceMigrationItem verifying = requireItem(item.getId());
        try (StorageSnapshot targetSnapshot = snapshotService.capture(targetService, targetRef)) {
            if (!hashEquals(verifying.getSourceHash(), targetSnapshot.sha256())) {
                throw new IllegalStateException(
                        "目标完整回读哈希不一致：源=" + verifying.getSourceHash()
                                + "，目标=" + targetSnapshot.sha256()
                );
            }
            if (verifying.getSnapshotSize() != null
                    && verifying.getSnapshotSize().longValue() != targetSnapshot.size()) {
                throw new IllegalStateException(
                        "目标完整回读大小不一致：源=" + verifying.getSnapshotSize()
                                + "，目标=" + targetSnapshot.size()
                );
            }

            Resource latestResource = requireFrozenActiveResource(verifying);
            if (!hashEquals(latestResource.getResourceHash(), verifying.getSourceHash())) {
                throw new ResourceMigrationSourceChangedException("资源内容哈希在目标校验期间发生变化");
            }

            ResourceLocation targetLocation = resourceLocationService.stageLocation(
                    verifying.getResourceId(),
                    verifying.getSourceLocationId(),
                    verifying.getSourceLocationVersion(),
                    verifying.getSourceHash(),
                    verifying.getTargetStoreType(),
                    verifying.getTargetStorageKey(),
                    verifying.getTargetPath(),
                    targetSnapshot.sha256(),
                    targetSnapshot.size(),
                    targetSnapshot.contentType(),
                    LocalDateTime.now()
            );
            taskStore.markVerified(verifying, targetSnapshot.sha256(), targetLocation.getId());
        }
    }

    private CleanupOutcome cleanupSource(ResourceMigrationItem item) throws Exception {
        Resource resource = requireResource(item.getResourceId());
        requireActiveContent(resource, "资源存在未完成的内容替换，不能清理迁移源副本");
        if (!Objects.equals(resource.getActiveLocationId(), item.getTargetLocationId())) {
            throw new IllegalStateException("当前活动副本已不是该任务验证的目标副本");
        }
        if (!hashEquals(resource.getResourceHash(), item.getTargetHash())
                || !hashEquals(item.getSourceHash(), item.getTargetHash())) {
            throw new IllegalStateException("当前资源哈希证据与迁移任务不一致");
        }

        ResourceLocation targetLocation = resourceLocationService.requireLocation(
                item.getResourceId(),
                item.getTargetLocationId()
        );
        if (!ResourceLocationStatus.ACTIVE.name().equals(targetLocation.getStatus())) {
            throw new IllegalStateException("目标物理副本当前不是活动状态");
        }
        StoreService targetService = fileStorageService.getFileStorageByStoreType(targetLocation.getStoreType());
        try (StorageSnapshot targetSnapshot = snapshotService.capture(
                targetService,
                locationRef(targetLocation, resource, item.getTargetHash())
        )) {
            if (!hashEquals(item.getTargetHash(), targetSnapshot.sha256())) {
                throw new IllegalStateException("清理前活动目标完整回读哈希不一致");
            }
        }

        ResourceLocation sourceLocation = resourceLocationService.requireLocation(
                item.getResourceId(),
                item.getSourceLocationId()
        );
        if (ResourceLocationStatus.MISSING.name().equals(sourceLocation.getStatus())) {
            taskStore.markSourceDeleted(item);
            return CleanupOutcome.MISSING;
        }
        if (ResourceLocationStatus.DELETED.name().equals(sourceLocation.getStatus())) {
            taskStore.markSourceDeleted(item);
            return CleanupOutcome.CLEANED;
        }
        if (!ResourceLocationStatus.RETAINED.name().equals(sourceLocation.getStatus())) {
            throw new IllegalStateException("源物理副本不是可清理的保留状态");
        }

        ResourceLocationDeleteResult deletion = resourceLocationDeleteService.delete(
                new ResourceLocationDeleteRequest(
                        item.getResourceId(),
                        sourceLocation.getId(),
                        null
                )
        );
        if (ResourceLocationStatus.DELETED.name().equals(deletion.status())) {
            taskStore.markSourceDeleted(item);
            return CleanupOutcome.CLEANED;
        }
        if (ResourceLocationStatus.MISSING.name().equals(deletion.status())) {
            taskStore.markSourceDeleted(item);
            return CleanupOutcome.MISSING;
        }
        return CleanupOutcome.FAILED;
    }

    private StorageResourceRef sourceRef(ResourceMigrationItem item, Resource resource) {
        return new StorageResourceRef(
                item.getResourceId(),
                item.getSourcePath(),
                item.getSourceStorageKey(),
                resource.getOriginalName(),
                item.getSourceSize(),
                item.getSourceExpectedHash(),
                item.getSourceMimeType()
        );
    }

    private StorageResourceRef targetRef(ResourceMigrationItem item, Resource resource) {
        return new StorageResourceRef(
                item.getResourceId(),
                item.getTargetPath(),
                item.getTargetStorageKey(),
                resource.getOriginalName(),
                item.getSnapshotSize(),
                item.getSourceHash(),
                item.getSourceMimeType()
        );
    }

    private StorageResourceRef locationRef(ResourceLocation location,
                                           Resource resource,
                                           String expectedHash) {
        return new StorageResourceRef(
                resource.getId(),
                location.getAccessPath(),
                location.getStorageKey(),
                resource.getOriginalName(),
                location.getSize(),
                expectedHash,
                location.getMimeType()
        );
    }

    private void validateDeterministicWriteResult(FileVO result,
                                                  String expectedPath,
                                                  String expectedKey) {
        if (result == null
                || !expectedPath.equals(result.getVisitPath())
                || !expectedKey.equals(result.getStorageKey())) {
            throw new IllegalStateException("目标存储返回了非确定性物理引用，无法安全恢复迁移");
        }
    }

    private String buildTargetKey(Resource resource, String contentHash) {
        if (!StringUtils.hasText(resource.getPublicId())) {
            throw new IllegalStateException("逻辑资源缺少稳定公开ID");
        }
        String hash = requireHash(contentHash, "源快照SHA-256不合法");
        return "resources/" + resource.getPublicId().toLowerCase(Locale.ROOT) + "/" + hash;
    }

    private ResourceMigrationItem requireItem(Long itemId) {
        ResourceMigrationItem item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new IllegalStateException("迁移条目不存在");
        }
        return item;
    }

    private ResourceMigrationItem requireItemOrFallback(ResourceMigrationItem fallback) {
        ResourceMigrationItem latest = itemMapper.selectById(fallback.getId());
        return latest == null ? fallback : latest;
    }

    private Resource requireResource(Integer resourceId) {
        Resource resource = resourceService.getById(resourceId);
        if (resource == null) {
            throw new IllegalStateException("逻辑资源不存在：" + resourceId);
        }
        return resource;
    }

    private Resource requireFrozenActiveResource(ResourceMigrationItem item) {
        Resource resource = requireResource(item.getResourceId());
        if (!ResourceContentState.isActive(resource.getContentState())) {
            throw new ResourceMigrationSourceChangedException("资源存在未完成的内容替换");
        }
        if (!Objects.equals(resource.getActiveLocationId(), item.getSourceLocationId())
                || normalizeVersion(resource.getLocationVersion())
                != normalizeVersion(item.getSourceLocationVersion())) {
            throw new ResourceMigrationSourceChangedException("资源活动副本在迁移期间发生变化");
        }
        return resource;
    }

    private void requireActiveContent(Resource resource, String message) {
        if (!ResourceContentState.isActive(resource.getContentState())) {
            throw new IllegalStateException(message);
        }
    }

    private String requireHash(String hash, String message) {
        if (!StringUtils.hasText(hash) || !hash.matches("(?i)[a-f0-9]{64}")) {
            throw new IllegalStateException(message);
        }
        return hash.toLowerCase(Locale.ROOT);
    }

    private boolean hashEquals(String left, String right) {
        return StringUtils.hasText(left)
                && StringUtils.hasText(right)
                && left.equalsIgnoreCase(right);
    }

    private int normalizeVersion(Integer version) {
        return version == null ? 0 : version;
    }

    private enum ExistingTarget {
        MISSING,
        MATCHED
    }

    private enum CleanupOutcome {
        CLEANED,
        MISSING,
        FAILED
    }
}