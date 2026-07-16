package com.ld.poetry.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ld.poetry.controller.dto.ResourceMigrationRequest;
import com.ld.poetry.dao.ResourceLocationMapper;
import com.ld.poetry.dao.ResourceMapper;
import com.ld.poetry.dao.ResourceMigrationItemMapper;
import com.ld.poetry.dao.ResourceMigrationTaskMapper;
import com.ld.poetry.entity.Resource;
import com.ld.poetry.entity.ResourceLocation;
import com.ld.poetry.entity.ResourceMigrationItem;
import com.ld.poetry.entity.ResourceMigrationTask;
import com.ld.poetry.enums.ResourceContentState;
import com.ld.poetry.enums.ResourceLocationStatus;
import com.ld.poetry.enums.ResourceMigrationItemStatus;
import com.ld.poetry.enums.ResourceMigrationTaskStatus;
import com.ld.poetry.utils.storage.StoreEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ResourceMigrationTaskStore {

    private static final String PENDING_TASK_IDS_SQL =
            "SELECT task_id FROM resource_migration_task WHERE status IN ('PENDING', 'RUNNING')";

    private final ResourceMigrationTaskMapper taskMapper;
    private final ResourceMigrationItemMapper itemMapper;
    private final ResourceLocationMapper resourceLocationMapper;
    private final ResourceMapper resourceMapper;
    private final ResourceService resourceService;

    @Transactional(rollbackFor = Exception.class)
    public ResourceMigrationTask createTask(ResourceMigrationRequest request,
                                            Integer createdBy,
                                            List<ResourceMigrationCandidate> candidates) {
        List<ResourceMigrationCandidate> frozenCandidates = freezeEligibleCandidates(candidates);
        String taskId = UUID.randomUUID().toString().replace("-", "");
        int skippedCount = (int) frozenCandidates.stream()
                .filter(candidate -> !candidate.eligible())
                .count();

        ResourceMigrationTask task = new ResourceMigrationTask();
        task.setTaskId(taskId);
        task.setCreatedBy(createdBy);
        task.setSourceStoreType(resolveTaskSourceStoreType(frozenCandidates));
        task.setTargetStoreType(request.targetStoreType());
        task.setScopeType(normalizeScopeType(request.scopeType()));
        task.setResourceType(request.resourceType());
        task.setKeepSource(true);
        task.setStatus(ResourceMigrationTaskStatus.PENDING.name());
        task.setTotalCount(frozenCandidates.size());
        task.setProcessedCount(skippedCount);
        task.setSuccessCount(0);
        task.setSkippedCount(skippedCount);
        task.setFailedCount(0);
        taskMapper.insert(task);

        for (ResourceMigrationCandidate candidate : frozenCandidates) {
            Resource resource = candidate.resource();
            ResourceLocation source = candidate.sourceLocation();
            ResourceMigrationItem item = new ResourceMigrationItem();
            item.setTaskId(taskId);
            item.setResourceId(resource.getId());
            item.setSourceLocationId(source == null ? null : source.getId());
            item.setSourceLocationVersion(normalizeVersion(resource.getLocationVersion()));
            item.setSourcePath(source == null ? safePath(resource.getPath()) : source.getAccessPath());
            item.setSourceStoreType(source == null
                    ? normalizeSourceStoreType(resource.getStoreType())
                    : normalizeSourceStoreType(source.getStoreType()));
            item.setSourceStorageKey(source == null ? resource.getStorageKey() : source.getStorageKey());
            item.setSourceExpectedHash(normalizeHash(resource.getResourceHash()));
            item.setSourceHashSource(resource.getHashSource());
            item.setSourceSize(source == null
                    ? resource.getSize() == null ? null : resource.getSize().longValue()
                    : source.getSize());
            item.setSourceMimeType(source == null ? resource.getMimeType() : source.getMimeType());
            item.setTargetStoreType(request.targetStoreType());
            item.setHashBaselined(false);
            item.setTargetCreated(false);
            item.setStatus(candidate.eligible()
                    ? ResourceMigrationItemStatus.PENDING.name()
                    : ResourceMigrationItemStatus.SKIPPED.name());
            item.setRetryCount(0);
            item.setSourceDeleted(false);
            if (!candidate.eligible()) {
                item.setErrorMessage(truncate(candidate.reason()));
                item.setFinishedAt(LocalDateTime.now());
            }
            itemMapper.insert(item);
        }
        return task;
    }

    private List<ResourceMigrationCandidate> freezeEligibleCandidates(
            List<ResourceMigrationCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("迁移候选不能为空");
        }
        Map<Integer, ResourceMigrationCandidate> frozenByResourceId = new HashMap<>();
        candidates.stream()
                .filter(ResourceMigrationCandidate::eligible)
                .sorted(Comparator.comparing(candidate -> candidate.resource().getId()))
                .forEach(candidate -> {
                    Resource expected = candidate.resource();
                    ResourceLocation expectedSource = candidate.sourceLocation();
                    if (expected == null || expected.getId() == null
                            || expectedSource == null || expectedSource.getId() == null) {
                        throw new ConcurrentModificationException("可迁移候选缺少资源或活动物理副本");
                    }
                    if (frozenByResourceId.containsKey(expected.getId())) {
                        throw new IllegalArgumentException("迁移候选包含重复资源：" + expected.getId());
                    }

                    Resource current = resourceMapper.selectByIdForUpdate(expected.getId());
                    if (current == null) {
                        throw new ConcurrentModificationException("逻辑资源在任务创建前被删除");
                    }
                    if (!ResourceContentState.isActive(current.getContentState())) {
                        throw new ConcurrentModificationException("资源存在未完成的内容替换，不能创建迁移任务");
                    }
                    if (!Objects.equals(current.getPath(), expected.getPath())
                            || !Objects.equals(current.getPublicId(), expected.getPublicId())
                            || !Objects.equals(current.getActiveLocationId(), expected.getActiveLocationId())
                            || normalizeVersion(current.getLocationVersion())
                            != normalizeVersion(expected.getLocationVersion())
                            || !Objects.equals(
                            normalizeHash(current.getResourceHash()),
                            normalizeHash(expected.getResourceHash())
                    )) {
                        throw new ConcurrentModificationException("资源在任务创建前发生变化，请重新预检");
                    }

                    ResourceLocation source = resourceLocationMapper.selectByIdForUpdate(expectedSource.getId());
                    if (source == null
                            || !current.getId().equals(source.getResourceId())
                            || !source.getId().equals(current.getActiveLocationId())
                            || !ResourceLocationStatus.ACTIVE.name().equals(source.getStatus())
                            || !Objects.equals(source.getStoreType(), expectedSource.getStoreType())
                            || !Objects.equals(source.getAccessPath(), expectedSource.getAccessPath())
                            || !Objects.equals(
                            normalizeBlank(source.getStorageKey()),
                            normalizeBlank(expectedSource.getStorageKey())
                    )
                            || !Objects.equals(
                            normalizeHash(source.getContentHash()),
                            normalizeHash(expectedSource.getContentHash())
                    )) {
                        throw new ConcurrentModificationException("活动物理副本在任务创建前发生变化，请重新预检");
                    }
                    frozenByResourceId.put(
                            current.getId(),
                            new ResourceMigrationCandidate(current, source, true, "")
                    );
                });

        return candidates.stream()
                .map(candidate -> candidate.eligible()
                        ? frozenByResourceId.get(candidate.resource().getId())
                        : candidate)
                .toList();
    }

    public boolean markRunning(ResourceMigrationTask task) {
        return taskMapper.update(
                null,
                Wrappers.<ResourceMigrationTask>lambdaUpdate()
                        .eq(ResourceMigrationTask::getId, task.getId())
                        .eq(ResourceMigrationTask::getStatus, ResourceMigrationTaskStatus.PENDING.name())
                        .set(ResourceMigrationTask::getStatus, ResourceMigrationTaskStatus.RUNNING.name())
                        .set(ResourceMigrationTask::getStartedAt,
                                task.getStartedAt() == null ? LocalDateTime.now() : task.getStartedAt())
                        .set(ResourceMigrationTask::getFinishedAt, null)
                        .set(ResourceMigrationTask::getErrorMessage, null)
        ) == 1;
    }

    public boolean markSnapshotting(ResourceMigrationItem item) {
        LambdaUpdateWrapper<ResourceMigrationItem> update = Wrappers.<ResourceMigrationItem>lambdaUpdate()
                .eq(ResourceMigrationItem::getId, item.getId())
                .eq(ResourceMigrationItem::getStatus, ResourceMigrationItemStatus.PENDING.name())
                .set(ResourceMigrationItem::getStatus, ResourceMigrationItemStatus.SNAPSHOTTING.name())
                .set(ResourceMigrationItem::getErrorMessage, null)
                .set(ResourceMigrationItem::getFinishedAt, null)
                .set(ResourceMigrationItem::getStartedAt,
                        item.getStartedAt() == null ? LocalDateTime.now() : item.getStartedAt());
        clearUnprovenSnapshot(update);
        return itemMapper.update(null, update) == 1;
    }

    public boolean resetSnapshot(ResourceMigrationItem item) {
        return itemMapper.update(
                null,
                Wrappers.<ResourceMigrationItem>lambdaUpdate()
                        .eq(ResourceMigrationItem::getId, item.getId())
                        .in(ResourceMigrationItem::getStatus,
                                ResourceMigrationItemStatus.SNAPSHOTTING.name(),
                                ResourceMigrationItemStatus.SNAPSHOT_READY.name())
                        .set(ResourceMigrationItem::getStatus, ResourceMigrationItemStatus.PENDING.name())
                        .set(ResourceMigrationItem::getSourceHash, null)
                        .set(ResourceMigrationItem::getSnapshotSize, null)
                        .set(ResourceMigrationItem::getTargetPath, null)
                        .set(ResourceMigrationItem::getTargetStorageKey, null)
                        .set(ResourceMigrationItem::getTargetHash, null)
                        .set(ResourceMigrationItem::getTargetLocationId, null)
                        .set(ResourceMigrationItem::getTargetCreated, false)
                        .set(ResourceMigrationItem::getErrorMessage, "临时快照不可复用，重新读取源文件")
        ) == 1;
    }

    public boolean resumeWriteAttempt(ResourceMigrationItem item) {
        boolean recoverable = hasRecoverableTargetReference(item);
        String targetStatus = recoverable
                ? ResourceMigrationItemStatus.TARGET_WRITTEN.name()
                : ResourceMigrationItemStatus.PENDING.name();
        var update = Wrappers.<ResourceMigrationItem>lambdaUpdate()
                .eq(ResourceMigrationItem::getId, item.getId())
                .in(ResourceMigrationItem::getStatus,
                        ResourceMigrationItemStatus.WRITING.name(),
                        ResourceMigrationItemStatus.UPLOADING.name())
                .set(ResourceMigrationItem::getStatus, targetStatus)
                .set(ResourceMigrationItem::getTargetHash, null)
                .set(ResourceMigrationItem::getTargetLocationId, null)
                .set(ResourceMigrationItem::getErrorMessage,
                        recoverable
                                ? "写入中断，按已持久化目标引用重新复验"
                                : "写入中断且目标引用不完整，重新读取源文件");
        if (!recoverable) {
            clearUnprovenSnapshot(update);
        }
        return itemMapper.update(null, update) == 1;
    }

    public boolean resetVerification(ResourceMigrationItem item) {
        boolean recoverable = hasRecoverableTargetReference(item);
        var update = Wrappers.<ResourceMigrationItem>lambdaUpdate()
                .eq(ResourceMigrationItem::getId, item.getId())
                .in(ResourceMigrationItem::getStatus,
                        ResourceMigrationItemStatus.VERIFYING.name(),
                        ResourceMigrationItemStatus.UPLOADED.name())
                .set(ResourceMigrationItem::getStatus,
                        recoverable
                                ? ResourceMigrationItemStatus.TARGET_WRITTEN.name()
                                : ResourceMigrationItemStatus.PENDING.name())
                .set(ResourceMigrationItem::getTargetHash, null)
                .set(ResourceMigrationItem::getTargetLocationId, null)
                .set(ResourceMigrationItem::getErrorMessage,
                        recoverable
                                ? "目标校验中断，重新完整回读"
                                : "目标校验中断且目标引用不完整，重新读取源文件");
        if (!recoverable) {
            clearUnprovenSnapshot(update);
        }
        return itemMapper.update(null, update) == 1;
    }

    public boolean resetMissingTarget(ResourceMigrationItem item) {
        return itemMapper.update(
                null,
                Wrappers.<ResourceMigrationItem>lambdaUpdate()
                        .eq(ResourceMigrationItem::getId, item.getId())
                        .eq(ResourceMigrationItem::getStatus, ResourceMigrationItemStatus.TARGET_WRITTEN.name())
                        .set(ResourceMigrationItem::getStatus, ResourceMigrationItemStatus.PENDING.name())
                        .set(ResourceMigrationItem::getSourceHash, null)
                        .set(ResourceMigrationItem::getSnapshotSize, null)
                        .set(ResourceMigrationItem::getTargetPath, null)
                        .set(ResourceMigrationItem::getTargetStorageKey, null)
                        .set(ResourceMigrationItem::getTargetHash, null)
                        .set(ResourceMigrationItem::getTargetLocationId, null)
                        .set(ResourceMigrationItem::getTargetCreated, false)
                        .set(ResourceMigrationItem::getErrorMessage, "目标副本不存在，重新读取源并写入")
        ) == 1;
    }

    /**
     * 将源快照证据、历史哈希基准和活动副本校验时间放在同一事务中提交。
     */
    @Transactional(rollbackFor = Exception.class)
    public void acceptSourceSnapshot(ResourceMigrationItem item,
                                     String sourceHash,
                                     long snapshotSize,
                                     String mimeType) {
        if (snapshotSize <= 0) {
            throw new IllegalArgumentException("源快照大小必须大于0");
        }
        String normalizedHash = requireHash(sourceHash);
        Resource resource = requireFrozenResource(item);
        ResourceLocation source = requireFrozenSource(item, resource);
        String expectedHash = normalizeAndValidateKnownHash(
                item.getSourceExpectedHash(),
                "任务创建时的资源基准哈希不合法"
        );
        String currentHash = normalizeAndValidateKnownHash(
                resource.getResourceHash(),
                "当前资源基准哈希不合法"
        );
        String locationHash = normalizeAndValidateKnownHash(
                source.getContentHash(),
                "冻结源副本哈希不合法"
        );

        if (StringUtils.hasText(expectedHash)
                && (!hashEquals(expectedHash, currentHash) || !hashEquals(expectedHash, normalizedHash))) {
            throw new ResourceMigrationSourceChangedException("源内容与任务创建时的基准哈希不一致");
        }
        if (StringUtils.hasText(currentHash) && !hashEquals(currentHash, normalizedHash)) {
            throw new ResourceMigrationSourceChangedException("源内容与当前资源基准哈希不一致");
        }
        if (StringUtils.hasText(locationHash) && !hashEquals(locationHash, normalizedHash)) {
            throw new ResourceMigrationSourceChangedException("源内容与冻结物理副本哈希不一致");
        }
        if (StringUtils.hasText(currentHash)
                && StringUtils.hasText(locationHash)
                && !hashEquals(currentHash, locationHash)) {
            throw new ResourceMigrationSourceChangedException("资源基准哈希与冻结物理副本哈希不一致");
        }

        LocalDateTime verifiedAt = LocalDateTime.now();
        boolean baselined = Boolean.TRUE.equals(item.getHashBaselined());
        if (!StringUtils.hasText(currentHash)) {
            boolean updated = resourceService.lambdaUpdate()
                    .eq(Resource::getId, item.getResourceId())
                    .eq(Resource::getActiveLocationId, item.getSourceLocationId())
                    .eq(Resource::getLocationVersion, normalizeVersion(item.getSourceLocationVersion()))
                    .eq(Resource::getContentState, ResourceContentState.ACTIVE.name())
                    .and(wrapper -> wrapper.isNull(Resource::getResourceHash)
                            .or()
                            .eq(Resource::getResourceHash, ""))
                    .set(Resource::getResourceHash, normalizedHash)
                    .set(Resource::getHashSource, "LEGACY_ADOPTION")
                    .set(Resource::getHashVerifiedAt, verifiedAt)
                    .update();
            if (!updated) {
                Resource latest = requireFrozenResource(item);
                if (!hashEquals(latest.getResourceHash(), normalizedHash)) {
                    throw new ResourceMigrationSourceChangedException("资源哈希基准在迁移期间发生变化");
                }
            } else {
                baselined = true;
            }
        } else {
            boolean updated = resourceService.lambdaUpdate()
                    .eq(Resource::getId, item.getResourceId())
                    .eq(Resource::getActiveLocationId, item.getSourceLocationId())
                    .eq(Resource::getLocationVersion, normalizeVersion(item.getSourceLocationVersion()))
                    .eq(Resource::getContentState, ResourceContentState.ACTIVE.name())
                    .eq(Resource::getResourceHash, resource.getResourceHash())
                    .set(Resource::getResourceHash, normalizedHash)
                    .set(Resource::getHashVerifiedAt, verifiedAt)
                    .update();
            if (!updated) {
                throw new ResourceMigrationSourceChangedException("资源在源快照校验期间发生变化");
            }
        }

        var locationUpdate = Wrappers.<ResourceLocation>lambdaUpdate()
                .eq(ResourceLocation::getId, source.getId())
                .eq(ResourceLocation::getResourceId, item.getResourceId())
                .eq(ResourceLocation::getStoreType, source.getStoreType())
                .eq(ResourceLocation::getAccessPath, source.getAccessPath())
                .eq(ResourceLocation::getStatus, ResourceLocationStatus.ACTIVE.name());
        appendNullableEquality(locationUpdate, ResourceLocation::getStorageKey, source.getStorageKey());
        appendNullableEquality(locationUpdate, ResourceLocation::getContentHash, source.getContentHash());
        int locationUpdated = resourceLocationMapper.update(
                null,
                locationUpdate.set(ResourceLocation::getContentHash, normalizedHash)
                        .set(ResourceLocation::getSize, snapshotSize)
                        .set(ResourceLocation::getMimeType, mimeType)
                        .set(ResourceLocation::getVerifiedAt, verifiedAt)
        );
        if (locationUpdated != 1) {
            throw new ResourceMigrationSourceChangedException("源物理副本在校验期间发生变化");
        }

        int itemUpdated = itemMapper.update(
                null,
                Wrappers.<ResourceMigrationItem>lambdaUpdate()
                        .eq(ResourceMigrationItem::getId, item.getId())
                        .eq(ResourceMigrationItem::getStatus, ResourceMigrationItemStatus.SNAPSHOTTING.name())
                        .set(ResourceMigrationItem::getStatus, ResourceMigrationItemStatus.SNAPSHOT_READY.name())
                        .set(ResourceMigrationItem::getSourceHash, normalizedHash)
                        .set(ResourceMigrationItem::getSnapshotSize, snapshotSize)
                        .set(ResourceMigrationItem::getSourceSize, snapshotSize)
                        .set(ResourceMigrationItem::getSourceMimeType, mimeType)
                        .set(ResourceMigrationItem::getHashBaselined, baselined)
                        .set(ResourceMigrationItem::getErrorMessage, null)
        );
        if (itemUpdated != 1) {
            throw new ConcurrentModificationException("源快照状态已变化");
        }
    }

    public boolean markWriting(ResourceMigrationItem item,
                               String targetPath,
                               String targetStorageKey) {
        if (!StringUtils.hasText(targetPath) || !StringUtils.hasText(targetStorageKey)) {
            throw new IllegalArgumentException("确定性目标地址和对象键不能为空");
        }
        return itemMapper.update(
                null,
                Wrappers.<ResourceMigrationItem>lambdaUpdate()
                        .eq(ResourceMigrationItem::getId, item.getId())
                        .eq(ResourceMigrationItem::getStatus, ResourceMigrationItemStatus.SNAPSHOT_READY.name())
                        .set(ResourceMigrationItem::getStatus, ResourceMigrationItemStatus.WRITING.name())
                        .set(ResourceMigrationItem::getTargetPath, targetPath)
                        .set(ResourceMigrationItem::getTargetStorageKey, targetStorageKey)
                        .set(ResourceMigrationItem::getTargetHash, null)
                        .set(ResourceMigrationItem::getTargetLocationId, null)
                        .set(ResourceMigrationItem::getTargetCreated, false)
                        .set(ResourceMigrationItem::getErrorMessage, null)
                        .set(ResourceMigrationItem::getFinishedAt, null)
        ) == 1;
    }

    /**
     * 回执型写入入口：目标存储不支持确定性对象键（如图床类 API），
     * 写入前无法预知目标地址，仅标记开始上传。中断后由 resumeWriteAttempt 回退到 PENDING 重新上传。
     */
    public boolean markUploading(ResourceMigrationItem item) {
        return itemMapper.update(
                null,
                Wrappers.<ResourceMigrationItem>lambdaUpdate()
                        .eq(ResourceMigrationItem::getId, item.getId())
                        .eq(ResourceMigrationItem::getStatus, ResourceMigrationItemStatus.SNAPSHOT_READY.name())
                        .set(ResourceMigrationItem::getStatus, ResourceMigrationItemStatus.UPLOADING.name())
                        .set(ResourceMigrationItem::getTargetPath, null)
                        .set(ResourceMigrationItem::getTargetStorageKey, null)
                        .set(ResourceMigrationItem::getTargetHash, null)
                        .set(ResourceMigrationItem::getTargetLocationId, null)
                        .set(ResourceMigrationItem::getTargetCreated, false)
                        .set(ResourceMigrationItem::getErrorMessage, null)
                        .set(ResourceMigrationItem::getFinishedAt, null)
        ) == 1;
    }

    /**
     * 回执型写入收据：记录图床服务端返回的物理地址和对象键。
     * 随后由 resetVerification 将 UPLOADED 转为 TARGET_WRITTEN，复用统一的完整回读验证流程。
     */
    public void markUploaded(ResourceMigrationItem item,
                             String targetPath,
                             String targetStorageKey) {
        if (!StringUtils.hasText(targetPath) || !StringUtils.hasText(targetStorageKey)) {
            throw new IllegalArgumentException("回执型写入返回的物理地址和对象键不能为空");
        }
        int updated = itemMapper.update(
                null,
                Wrappers.<ResourceMigrationItem>lambdaUpdate()
                        .eq(ResourceMigrationItem::getId, item.getId())
                        .eq(ResourceMigrationItem::getStatus, ResourceMigrationItemStatus.UPLOADING.name())
                        .set(ResourceMigrationItem::getStatus, ResourceMigrationItemStatus.UPLOADED.name())
                        .set(ResourceMigrationItem::getTargetPath, targetPath)
                        .set(ResourceMigrationItem::getTargetStorageKey, targetStorageKey)
                        .set(ResourceMigrationItem::getTargetCreated, true)
                        .set(ResourceMigrationItem::getTargetHash, null)
                        .set(ResourceMigrationItem::getTargetLocationId, null)
                        .set(ResourceMigrationItem::getErrorMessage, null)
        );
        if (updated != 1) {
            throw new ConcurrentModificationException("回执型上传状态已变化");
        }
    }

    public void markTargetWritten(ResourceMigrationItem item,
                                  String targetPath,
                                  String targetStorageKey,
                                  boolean targetCreated) {
        if (!StringUtils.hasText(targetPath)) {
            throw new IllegalArgumentException("目标物理地址不能为空");
        }
        int updated = itemMapper.update(
                null,
                Wrappers.<ResourceMigrationItem>lambdaUpdate()
                        .eq(ResourceMigrationItem::getId, item.getId())
                        .eq(ResourceMigrationItem::getStatus, ResourceMigrationItemStatus.WRITING.name())
                        .set(ResourceMigrationItem::getStatus, ResourceMigrationItemStatus.TARGET_WRITTEN.name())
                        .set(ResourceMigrationItem::getTargetPath, targetPath)
                        .set(ResourceMigrationItem::getTargetStorageKey, targetStorageKey)
                        .set(ResourceMigrationItem::getTargetCreated, targetCreated)
                        .set(ResourceMigrationItem::getTargetHash, null)
                        .set(ResourceMigrationItem::getTargetLocationId, null)
                        .set(ResourceMigrationItem::getErrorMessage, null)
        );
        if (updated != 1) {
            throw new ConcurrentModificationException("目标写入状态已变化");
        }
    }

    public boolean markVerifying(ResourceMigrationItem item) {
        return transition(
                item,
                ResourceMigrationItemStatus.TARGET_WRITTEN,
                ResourceMigrationItemStatus.VERIFYING,
                false
        );
    }

    public void markVerified(ResourceMigrationItem item, String targetHash, Long targetLocationId) {
        int updated = itemMapper.update(
                null,
                Wrappers.<ResourceMigrationItem>lambdaUpdate()
                        .eq(ResourceMigrationItem::getId, item.getId())
                        .eq(ResourceMigrationItem::getStatus, ResourceMigrationItemStatus.VERIFYING.name())
                        .set(ResourceMigrationItem::getStatus, ResourceMigrationItemStatus.VERIFIED.name())
                        .set(ResourceMigrationItem::getTargetHash, requireHash(targetHash))
                        .set(ResourceMigrationItem::getTargetLocationId, targetLocationId)
                        .set(ResourceMigrationItem::getErrorMessage, null)
        );
        if (updated != 1) {
            throw new ConcurrentModificationException("目标校验状态已变化");
        }
    }

    public void markSuccess(ResourceMigrationItem item, boolean sourceDeleted) {
        markSuccess(item, sourceDeleted, null);
    }

    public void markSuccess(ResourceMigrationItem item, boolean sourceDeleted, String warning) {
        int updated = itemMapper.update(
                null,
                Wrappers.<ResourceMigrationItem>lambdaUpdate()
                        .eq(ResourceMigrationItem::getId, item.getId())
                        .eq(ResourceMigrationItem::getStatus, ResourceMigrationItemStatus.SWITCHED.name())
                        .set(ResourceMigrationItem::getStatus, ResourceMigrationItemStatus.SUCCESS.name())
                        .set(ResourceMigrationItem::getSourceDeleted, sourceDeleted)
                        .set(ResourceMigrationItem::getFinishedAt, LocalDateTime.now())
                        .set(ResourceMigrationItem::getErrorMessage, warning == null ? null : truncate(warning))
        );
        if (updated != 1) {
            ResourceMigrationItem latest = itemMapper.selectById(item.getId());
            if (latest == null || !ResourceMigrationItemStatus.SUCCESS.name().equals(latest.getStatus())) {
                throw new ConcurrentModificationException("迁移成功状态已变化");
            }
        }
    }

    public void markSourceChanged(ResourceMigrationItem item, String message) {
        markTerminalFailure(item, ResourceMigrationItemStatus.SOURCE_CHANGED, message);
    }

    public void markFailed(ResourceMigrationItem item, String message) {
        markTerminalFailure(item, ResourceMigrationItemStatus.FAILED, message);
    }

    /**
     * 保留旧调用签名；严格迁移失败时不丢弃目标引用，避免产生无法审计的孤儿副本。
     */
    public void markFailed(ResourceMigrationItem item, String message, boolean ignoredClearTarget) {
        markFailed(item, message);
    }

    public void markSourceDeleted(ResourceMigrationItem item) {
        ResourceMigrationItem update = new ResourceMigrationItem();
        update.setId(item.getId());
        update.setSourceDeleted(true);
        itemMapper.updateById(update);
    }

    public ResourceMigrationTask refreshProgress(String taskId) {
        ResourceMigrationTask task = findTask(taskId);
        EnumMap<ResourceMigrationItemStatus, Integer> counts = statusCounts(taskId);
        int success = counts.getOrDefault(ResourceMigrationItemStatus.SUCCESS, 0);
        int skipped = counts.getOrDefault(ResourceMigrationItemStatus.SKIPPED, 0);
        int failed = counts.getOrDefault(ResourceMigrationItemStatus.FAILED, 0)
                + counts.getOrDefault(ResourceMigrationItemStatus.SOURCE_CHANGED, 0);

        ResourceMigrationTask update = new ResourceMigrationTask();
        update.setId(task.getId());
        update.setProcessedCount(success + skipped + failed);
        update.setSuccessCount(success);
        update.setSkippedCount(skipped);
        update.setFailedCount(failed);
        taskMapper.updateById(update);
        return findTask(taskId);
    }

    public ResourceMigrationTask finishTask(String taskId, boolean cancelled, String errorMessage) {
        ResourceMigrationTask task = refreshProgress(taskId);
        ResourceMigrationTaskStatus current = ResourceMigrationTaskStatus.valueOf(task.getStatus());
        if (current.isTerminal()) {
            return task;
        }

        ResourceMigrationTaskStatus status;
        if (cancelled) {
            status = ResourceMigrationTaskStatus.CANCELLED;
        } else if (StringUtils.hasText(errorMessage)
                || task.getFailedCount() != null && task.getFailedCount() > 0
                || task.getProcessedCount() == null
                || task.getTotalCount() == null
                || task.getProcessedCount() < task.getTotalCount()) {
            status = task.getSuccessCount() != null && task.getSuccessCount() > 0
                    ? ResourceMigrationTaskStatus.PARTIAL_SUCCESS
                    : ResourceMigrationTaskStatus.FAILED;
            if (!StringUtils.hasText(errorMessage)
                    && task.getProcessedCount() != null
                    && task.getTotalCount() != null
                    && task.getProcessedCount() < task.getTotalCount()) {
                errorMessage = "任务仍有未完成条目，可重试后继续收尾";
            }
        } else {
            status = ResourceMigrationTaskStatus.SUCCESS;
        }

        taskMapper.update(
                null,
                Wrappers.<ResourceMigrationTask>lambdaUpdate()
                        .eq(ResourceMigrationTask::getId, task.getId())
                        .in(ResourceMigrationTask::getStatus,
                                ResourceMigrationTaskStatus.PENDING.name(),
                                ResourceMigrationTaskStatus.RUNNING.name())
                        .set(ResourceMigrationTask::getStatus, status.name())
                        .set(ResourceMigrationTask::getFinishedAt, LocalDateTime.now())
                        .set(ResourceMigrationTask::getErrorMessage,
                                errorMessage == null ? null : truncate(errorMessage))
        );
        return findTask(taskId);
    }

    public boolean cancelTask(String taskId) {
        ResourceMigrationTask task = findTask(taskId);
        ResourceMigrationTaskStatus current = ResourceMigrationTaskStatus.valueOf(task.getStatus());
        if (current.isTerminal()) {
            return false;
        }
        return taskMapper.update(
                null,
                Wrappers.<ResourceMigrationTask>lambdaUpdate()
                        .eq(ResourceMigrationTask::getId, task.getId())
                        .in(ResourceMigrationTask::getStatus,
                                ResourceMigrationTaskStatus.PENDING.name(),
                                ResourceMigrationTaskStatus.RUNNING.name())
                        .set(ResourceMigrationTask::getStatus, ResourceMigrationTaskStatus.CANCELLED.name())
                        .set(ResourceMigrationTask::getFinishedAt, LocalDateTime.now())
        ) == 1;
    }

    @Transactional(rollbackFor = Exception.class)
    public ResourceMigrationTask prepareRetry(String taskId) {
        ResourceMigrationTask task = findTask(taskId);
        ResourceMigrationTaskStatus current = ResourceMigrationTaskStatus.valueOf(task.getStatus());
        if (current != ResourceMigrationTaskStatus.FAILED
                && current != ResourceMigrationTaskStatus.PARTIAL_SUCCESS) {
            throw new IllegalArgumentException("只有失败或部分成功的任务可以重试");
        }

        int claimed = taskMapper.update(
                null,
                Wrappers.<ResourceMigrationTask>lambdaUpdate()
                        .eq(ResourceMigrationTask::getId, task.getId())
                        .in(ResourceMigrationTask::getStatus,
                                ResourceMigrationTaskStatus.FAILED.name(),
                                ResourceMigrationTaskStatus.PARTIAL_SUCCESS.name())
                        .set(ResourceMigrationTask::getStatus, ResourceMigrationTaskStatus.PENDING.name())
                        .set(ResourceMigrationTask::getFinishedAt, null)
                        .set(ResourceMigrationTask::getErrorMessage, null)
        );
        if (claimed != 1) {
            throw new IllegalArgumentException("任务状态已变化，请刷新后重试");
        }

        markRecoverableItemsForVerification(
                Wrappers.<ResourceMigrationItem>lambdaUpdate()
                        .eq(ResourceMigrationItem::getTaskId, taskId)
                        .eq(ResourceMigrationItem::getStatus, ResourceMigrationItemStatus.FAILED.name()),
                null
        );
        resetUnprovenItems(
                Wrappers.<ResourceMigrationItem>lambdaUpdate()
                        .eq(ResourceMigrationItem::getTaskId, taskId)
                        .eq(ResourceMigrationItem::getStatus, ResourceMigrationItemStatus.FAILED.name()),
                null
        );
        return refreshProgress(taskId);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<String> recoverPendingTaskIds() {
        LambdaUpdateWrapper<ResourceMigrationItem> lostSnapshots = pendingTaskItems()
                .in(ResourceMigrationItem::getStatus,
                        ResourceMigrationItemStatus.SNAPSHOTTING.name(),
                        ResourceMigrationItemStatus.SNAPSHOT_READY.name())
                .set(ResourceMigrationItem::getStatus, ResourceMigrationItemStatus.PENDING.name())
                .set(ResourceMigrationItem::getErrorMessage, "服务重启后重新读取源快照");
        clearUnprovenSnapshot(lostSnapshots);
        itemMapper.update(null, lostSnapshots);

        recoverInterruptedItems(
                List.of(
                        ResourceMigrationItemStatus.WRITING,
                        ResourceMigrationItemStatus.UPLOADING
                ),
                "服务重启后按完整目标引用恢复"
        );
        recoverInterruptedItems(
                List.of(
                        ResourceMigrationItemStatus.VERIFYING,
                        ResourceMigrationItemStatus.VERIFIED,
                        ResourceMigrationItemStatus.UPLOADED
                ),
                "服务重启后重新完整回读目标"
        );

        taskMapper.update(
                null,
                Wrappers.<ResourceMigrationTask>lambdaUpdate()
                        .eq(ResourceMigrationTask::getStatus, ResourceMigrationTaskStatus.RUNNING.name())
                        .set(ResourceMigrationTask::getStatus, ResourceMigrationTaskStatus.PENDING.name())
                        .set(ResourceMigrationTask::getErrorMessage, "服务重启后自动恢复")
        );
        return taskMapper.selectList(
                Wrappers.<ResourceMigrationTask>lambdaQuery()
                        .eq(ResourceMigrationTask::getStatus, ResourceMigrationTaskStatus.PENDING.name())
                        .orderByAsc(ResourceMigrationTask::getCreateTime)
        ).stream().map(ResourceMigrationTask::getTaskId).toList();
    }

    public ResourceMigrationTask findTask(String taskId) {
        ResourceMigrationTask task = taskMapper.selectOne(
                Wrappers.<ResourceMigrationTask>lambdaQuery()
                        .eq(ResourceMigrationTask::getTaskId, taskId)
                        .last("limit 1")
        );
        if (task == null) {
            throw new IllegalArgumentException("迁移任务不存在");
        }
        return task;
    }

    private boolean transition(ResourceMigrationItem item,
                               ResourceMigrationItemStatus expected,
                               ResourceMigrationItemStatus target,
                               boolean markStarted) {
        var update = Wrappers.<ResourceMigrationItem>lambdaUpdate()
                .eq(ResourceMigrationItem::getId, item.getId())
                .eq(ResourceMigrationItem::getStatus, expected.name())
                .set(ResourceMigrationItem::getStatus, target.name())
                .set(ResourceMigrationItem::getErrorMessage, null)
                .set(ResourceMigrationItem::getFinishedAt, null);
        if (markStarted) {
            update.set(ResourceMigrationItem::getStartedAt,
                    item.getStartedAt() == null ? LocalDateTime.now() : item.getStartedAt());
        }
        return itemMapper.update(null, update) == 1;
    }

    private void markTerminalFailure(ResourceMigrationItem item,
                                     ResourceMigrationItemStatus status,
                                     String message) {
        itemMapper.update(
                null,
                Wrappers.<ResourceMigrationItem>lambdaUpdate()
                        .eq(ResourceMigrationItem::getId, item.getId())
                        .notIn(ResourceMigrationItem::getStatus,
                                ResourceMigrationItemStatus.SUCCESS.name(),
                                ResourceMigrationItemStatus.SKIPPED.name(),
                                ResourceMigrationItemStatus.SOURCE_CHANGED.name(),
                                ResourceMigrationItemStatus.SWITCHED.name())
                        .set(ResourceMigrationItem::getStatus, status.name())
                        .set(ResourceMigrationItem::getErrorMessage, truncate(message))
                        .set(ResourceMigrationItem::getFinishedAt, LocalDateTime.now())
                        .setSql("retry_count = COALESCE(retry_count, 0) + 1")
        );
    }

    private Resource requireFrozenResource(ResourceMigrationItem item) {
        Resource resource = resourceService.getById(item.getResourceId());
        if (resource == null) {
            throw new ResourceMigrationSourceChangedException("逻辑资源在迁移期间被删除");
        }
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

    private ResourceLocation requireFrozenSource(ResourceMigrationItem item, Resource resource) {
        if (item.getSourceLocationId() == null) {
            throw new IllegalStateException("迁移条目缺少冻结源位置");
        }
        ResourceLocation source = resourceLocationMapper.selectById(item.getSourceLocationId());
        if (source == null
                || !resource.getId().equals(source.getResourceId())
                || !ResourceLocationStatus.ACTIVE.name().equals(source.getStatus())) {
            throw new ResourceMigrationSourceChangedException("冻结源物理副本状态已变化");
        }
        if (!normalizeSourceStoreType(item.getSourceStoreType()).equals(source.getStoreType())
                || !Objects.equals(item.getSourcePath(), source.getAccessPath())
                || !Objects.equals(normalizeBlank(item.getSourceStorageKey()),
                normalizeBlank(source.getStorageKey()))) {
            throw new ResourceMigrationSourceChangedException("冻结源物理引用在迁移期间发生变化");
        }
        return source;
    }

    private EnumMap<ResourceMigrationItemStatus, Integer> statusCounts(String taskId) {
        EnumMap<ResourceMigrationItemStatus, Integer> counts = new EnumMap<>(ResourceMigrationItemStatus.class);
        for (Map<String, Object> row : itemMapper.countByStatus(taskId)) {
            String status = String.valueOf(valueIgnoreCase(row, "status"));
            Object countValue = valueIgnoreCase(row, "item_count");
            if (countValue instanceof Number number) {
                counts.put(ResourceMigrationItemStatus.valueOf(status), number.intValue());
            }
        }
        return counts;
    }

    private Object valueIgnoreCase(Map<String, Object> row, String key) {
        return row.entrySet().stream()
                .filter(entry -> key.equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private String resolveTaskSourceStoreType(List<ResourceMigrationCandidate> candidates) {
        List<String> sourceTypes = candidates.stream()
                .filter(ResourceMigrationCandidate::eligible)
                .map(ResourceMigrationCandidate::sourceLocation)
                .filter(Objects::nonNull)
                .map(ResourceLocation::getStoreType)
                .map(this::normalizeSourceStoreType)
                .distinct()
                .toList();
        return sourceTypes.size() == 1 ? sourceTypes.getFirst() : "MIXED";
    }

    private String normalizeScopeType(String scopeType) {
        return "FILTER".equalsIgnoreCase(scopeType) ? "FILTER" : "SELECTED";
    }

    private String normalizeSourceStoreType(String storeType) {
        return StringUtils.hasText(storeType) ? storeType : StoreEnum.LOCAL.getCode();
    }

    private String safePath(String path) {
        return StringUtils.hasText(path) ? path : "/missing-resource-path";
    }

    private void appendNullableEquality(LambdaUpdateWrapper<ResourceLocation> update,
                                        com.baomidou.mybatisplus.core.toolkit.support.SFunction<ResourceLocation, String> column,
                                        String value) {
        if (StringUtils.hasText(value)) {
            update.eq(column, value);
        } else {
            update.and(wrapper -> wrapper.isNull(column).or().eq(column, ""));
        }
    }

    private void recoverInterruptedItems(List<ResourceMigrationItemStatus> statuses,
                                         String recoveryMessage) {
        List<String> statusNames = statuses.stream().map(Enum::name).toList();
        markRecoverableItemsForVerification(
                pendingTaskItems().in(ResourceMigrationItem::getStatus, statusNames),
                recoveryMessage
        );
        resetUnprovenItems(
                pendingTaskItems().in(ResourceMigrationItem::getStatus, statusNames),
                "服务重启后中间证据不完整，重新读取源文件"
        );
    }

    private void markRecoverableItemsForVerification(
            LambdaUpdateWrapper<ResourceMigrationItem> update,
            String message) {
        itemMapper.update(
                null,
                update.isNotNull(ResourceMigrationItem::getTargetPath)
                        .ne(ResourceMigrationItem::getTargetPath, "")
                        .isNotNull(ResourceMigrationItem::getTargetStorageKey)
                        .ne(ResourceMigrationItem::getTargetStorageKey, "")
                        .apply("source_hash REGEXP '^[0-9A-Fa-f]{64}$'")
                        .set(ResourceMigrationItem::getStatus,
                                ResourceMigrationItemStatus.TARGET_WRITTEN.name())
                        .set(ResourceMigrationItem::getTargetHash, null)
                        .set(ResourceMigrationItem::getTargetLocationId, null)
                        .set(ResourceMigrationItem::getErrorMessage, message)
                        .set(ResourceMigrationItem::getFinishedAt, null)
        );
    }

    private void resetUnprovenItems(LambdaUpdateWrapper<ResourceMigrationItem> update,
                                    String message) {
        update.set(ResourceMigrationItem::getStatus, ResourceMigrationItemStatus.PENDING.name())
                .set(ResourceMigrationItem::getErrorMessage, message)
                .set(ResourceMigrationItem::getFinishedAt, null);
        clearUnprovenSnapshot(update);
        itemMapper.update(null, update);
    }

    private LambdaUpdateWrapper<ResourceMigrationItem> pendingTaskItems() {
        return Wrappers.<ResourceMigrationItem>lambdaUpdate()
                .inSql(ResourceMigrationItem::getTaskId, PENDING_TASK_IDS_SQL);
    }

    private void clearUnprovenSnapshot(LambdaUpdateWrapper<ResourceMigrationItem> update) {
        update.set(ResourceMigrationItem::getSourceHash, null)
                .set(ResourceMigrationItem::getSnapshotSize, null)
                .set(ResourceMigrationItem::getTargetPath, null)
                .set(ResourceMigrationItem::getTargetStorageKey, null)
                .set(ResourceMigrationItem::getTargetHash, null)
                .set(ResourceMigrationItem::getTargetLocationId, null)
                .set(ResourceMigrationItem::getTargetCreated, false);
    }

    private boolean hasRecoverableTargetReference(ResourceMigrationItem item) {
        return StringUtils.hasText(item.getTargetPath())
                && StringUtils.hasText(item.getTargetStorageKey())
                && StringUtils.hasText(item.getSourceHash())
                && item.getSourceHash().matches("(?i)[a-f0-9]{64}");
    }

    private String normalizeBlank(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private String normalizeHash(String hash) {
        return StringUtils.hasText(hash) ? hash.toLowerCase(Locale.ROOT) : null;
    }

    private String normalizeAndValidateKnownHash(String hash, String message) {
        String normalized = normalizeHash(hash);
        if (normalized != null && !normalized.matches("[a-f0-9]{64}")) {
            throw new ResourceMigrationSourceChangedException(message);
        }
        return normalized;
    }

    private String requireHash(String hash) {
        String normalized = normalizeHash(hash);
        if (normalized == null || !normalized.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("SHA-256 内容哈希不合法");
        }
        return normalized;
    }

    private boolean hashEquals(String left, String right) {
        return StringUtils.hasText(left)
                && StringUtils.hasText(right)
                && left.equalsIgnoreCase(right);
    }

    private int normalizeVersion(Integer version) {
        return version == null ? 0 : version;
    }

    private String truncate(String message) {
        String normalized = !StringUtils.hasText(message) ? "迁移失败" : message;
        return normalized.length() <= 1000 ? normalized : normalized.substring(0, 1000);
    }
}