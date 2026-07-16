package com.ld.poetry.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ld.poetry.dao.ResourceAdoptionItemMapper;
import com.ld.poetry.dao.ResourceAdoptionTaskMapper;
import com.ld.poetry.entity.ResourceAdoptionItem;
import com.ld.poetry.entity.ResourceAdoptionTask;
import com.ld.poetry.enums.ResourceAdoptionItemStatus;
import com.ld.poetry.enums.ResourceAdoptionTaskStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ResourceAdoptionTaskStore {

    private final ResourceAdoptionTaskMapper taskMapper;
    private final ResourceAdoptionItemMapper itemMapper;

    @Transactional(rollbackFor = Exception.class)
    public ResourceAdoptionTask createTask(Integer createdBy, List<ResourceAdoptionCandidate> candidates) {
        if (createdBy == null || candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("接管任务创建人和候选不能为空");
        }
        if (candidates.stream().noneMatch(ResourceAdoptionCandidate::trusted)) {
            throw new IllegalArgumentException("所选范围没有可自动接管的可信资源");
        }

        String taskId = UUID.randomUUID().toString().replace("-", "");
        int skipped = (int) candidates.stream().filter(candidate -> !candidate.trusted()).count();
        ResourceAdoptionTask task = new ResourceAdoptionTask();
        task.setTaskId(taskId);
        task.setCreatedBy(createdBy);
        task.setStatus(ResourceAdoptionTaskStatus.PENDING.name());
        task.setTotalCount(candidates.size());
        task.setProcessedCount(skipped);
        task.setSuccessCount(0);
        task.setSkippedCount(skipped);
        task.setFailedCount(0);
        if (taskMapper.insert(task) != 1 || task.getId() == null) {
            throw new IllegalStateException("历史接管任务创建失败");
        }

        for (ResourceAdoptionCandidate candidate : candidates) {
            ResourceAdoptionItem item = new ResourceAdoptionItem();
            item.setTaskId(taskId);
            item.setSourceUrl(candidate.sourceUrl());
            item.setReferenceCount(candidate.referenceCount());
            item.setHashBaselined(false);
            item.setStatus(candidate.trusted()
                    ? ResourceAdoptionItemStatus.PENDING.name()
                    : ResourceAdoptionItemStatus.SKIPPED.name());
            if (!candidate.trusted()) {
                item.setErrorMessage(truncate(candidate.reason()));
            }
            if (itemMapper.insert(item) != 1) {
                throw new IllegalStateException("历史接管条目创建失败");
            }
        }
        return task;
    }

    @Transactional(rollbackFor = Exception.class)
    public ClaimedItem claimNext(String taskId) {
        ResourceAdoptionTask task = taskMapper.findByTaskIdForUpdate(taskId);
        if (task == null) {
            throw new IllegalArgumentException("历史接管任务不存在");
        }
        ResourceAdoptionTaskStatus status = ResourceAdoptionTaskStatus.valueOf(task.getStatus());
        if (status == ResourceAdoptionTaskStatus.PENDING) {
            task.setStatus(ResourceAdoptionTaskStatus.RUNNING.name());
            if (task.getStartedAt() == null) {
                task.setStartedAt(LocalDateTime.now());
            }
            task.setFinishedAt(null);
            task.setErrorMessage(null);
            if (taskMapper.updateById(task) != 1) {
                throw new ConcurrentModificationException("历史接管任务启动状态已变化");
            }
        } else if (status != ResourceAdoptionTaskStatus.RUNNING) {
            return null;
        }

        ResourceAdoptionItem item = itemMapper.findNextPendingForUpdate(taskId);
        if (item == null) {
            return null;
        }
        int updated = itemMapper.update(
                null,
                Wrappers.<ResourceAdoptionItem>lambdaUpdate()
                        .eq(ResourceAdoptionItem::getId, item.getId())
                        .eq(ResourceAdoptionItem::getStatus, ResourceAdoptionItemStatus.PENDING.name())
                        .set(ResourceAdoptionItem::getStatus, ResourceAdoptionItemStatus.READING.name())
                        .set(ResourceAdoptionItem::getResourceId, null)
                        .set(ResourceAdoptionItem::getSourceHash, null)
                        .set(ResourceAdoptionItem::getSnapshotSize, null)
                        .set(ResourceAdoptionItem::getHashBaselined, false)
                        .set(ResourceAdoptionItem::getErrorMessage, null)
        );
        if (updated != 1) {
            throw new ConcurrentModificationException("历史接管条目领取状态已变化");
        }
        item.setStatus(ResourceAdoptionItemStatus.READING.name());
        item.setResourceId(null);
        item.setSourceHash(null);
        item.setSnapshotSize(null);
        item.setHashBaselined(false);
        item.setErrorMessage(null);
        return new ClaimedItem(task, item);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markFailed(Long itemId, String errorMessage) {
        ResourceAdoptionItem current = itemMapper.selectById(itemId);
        if (current == null) {
            return;
        }
        ResourceAdoptionTask task = taskMapper.findByTaskIdForUpdate(current.getTaskId());
        ResourceAdoptionItem item = itemMapper.selectByIdForUpdate(itemId);
        if (task == null || item == null
                || !ResourceAdoptionItemStatus.READING.name().equals(item.getStatus())) {
            return;
        }
        if (ResourceAdoptionTaskStatus.CANCELLED.name().equals(task.getStatus())) {
            return;
        }
        int updated = itemMapper.update(
                null,
                Wrappers.<ResourceAdoptionItem>lambdaUpdate()
                        .eq(ResourceAdoptionItem::getId, itemId)
                        .eq(ResourceAdoptionItem::getStatus, ResourceAdoptionItemStatus.READING.name())
                        .set(ResourceAdoptionItem::getStatus, ResourceAdoptionItemStatus.FAILED.name())
                        .set(ResourceAdoptionItem::getResourceId, null)
                        .set(ResourceAdoptionItem::getSourceHash, null)
                        .set(ResourceAdoptionItem::getSnapshotSize, null)
                        .set(ResourceAdoptionItem::getHashBaselined, false)
                        .set(ResourceAdoptionItem::getErrorMessage, truncate(errorMessage))
        );
        if (updated != 1) {
            throw new ConcurrentModificationException("历史接管失败状态已变化");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public ResourceAdoptionTask refreshProgress(String taskId) {
        ResourceAdoptionTask task = taskMapper.findByTaskIdForUpdate(taskId);
        if (task == null) {
            throw new IllegalArgumentException("历史接管任务不存在");
        }
        List<ResourceAdoptionItem> items = itemMapper.selectList(
                Wrappers.<ResourceAdoptionItem>lambdaQuery()
                        .select(ResourceAdoptionItem::getStatus)
                        .eq(ResourceAdoptionItem::getTaskId, taskId)
        );
        int adopted = count(items, ResourceAdoptionItemStatus.ADOPTED);
        int skipped = count(items, ResourceAdoptionItemStatus.SKIPPED);
        int failed = count(items, ResourceAdoptionItemStatus.FAILED);
        int processed = adopted + skipped + failed;
        boolean unfinished = items.stream().anyMatch(item ->
                ResourceAdoptionItemStatus.PENDING.name().equals(item.getStatus())
                        || ResourceAdoptionItemStatus.READING.name().equals(item.getStatus()));

        task.setProcessedCount(processed);
        task.setSuccessCount(adopted);
        task.setSkippedCount(skipped);
        task.setFailedCount(failed);
        if (!ResourceAdoptionTaskStatus.CANCELLED.name().equals(task.getStatus()) && !unfinished) {
            task.setStatus(resolveTerminalStatus(adopted, failed).name());
            task.setFinishedAt(LocalDateTime.now());
            task.setErrorMessage(failed > 0 ? "部分历史资源接管失败，请查看条目详情" : null);
        }
        if (taskMapper.updateById(task) != 1) {
            throw new ConcurrentModificationException("历史接管任务进度状态已变化");
        }
        return task;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean cancel(String taskId) {
        ResourceAdoptionTask task = taskMapper.findByTaskIdForUpdate(taskId);
        if (task == null) {
            throw new IllegalArgumentException("历史接管任务不存在");
        }
        ResourceAdoptionTaskStatus status = ResourceAdoptionTaskStatus.valueOf(task.getStatus());
        if (status.isTerminal()) {
            return status == ResourceAdoptionTaskStatus.CANCELLED;
        }
        itemMapper.update(
                null,
                Wrappers.<ResourceAdoptionItem>lambdaUpdate()
                        .eq(ResourceAdoptionItem::getTaskId, taskId)
                        .in(ResourceAdoptionItem::getStatus,
                                ResourceAdoptionItemStatus.PENDING.name(),
                                ResourceAdoptionItemStatus.READING.name())
                        .set(ResourceAdoptionItem::getStatus, ResourceAdoptionItemStatus.SKIPPED.name())
                        .set(ResourceAdoptionItem::getResourceId, null)
                        .set(ResourceAdoptionItem::getSourceHash, null)
                        .set(ResourceAdoptionItem::getSnapshotSize, null)
                        .set(ResourceAdoptionItem::getHashBaselined, false)
                        .set(ResourceAdoptionItem::getErrorMessage, "任务已取消")
        );
        task.setStatus(ResourceAdoptionTaskStatus.CANCELLED.name());
        task.setFinishedAt(LocalDateTime.now());
        task.setErrorMessage("任务已取消");
        if (taskMapper.updateById(task) != 1) {
            throw new ConcurrentModificationException("历史接管任务取消状态已变化");
        }
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public ResourceAdoptionTask prepareRetry(String taskId) {
        ResourceAdoptionTask task = taskMapper.findByTaskIdForUpdate(taskId);
        if (task == null) {
            throw new IllegalArgumentException("历史接管任务不存在");
        }
        ResourceAdoptionTaskStatus status = ResourceAdoptionTaskStatus.valueOf(task.getStatus());
        if (status != ResourceAdoptionTaskStatus.FAILED
                && status != ResourceAdoptionTaskStatus.PARTIAL_SUCCESS) {
            throw new IllegalArgumentException("只有失败或部分成功的接管任务可以重试");
        }
        int reset = itemMapper.update(
                null,
                Wrappers.<ResourceAdoptionItem>lambdaUpdate()
                        .eq(ResourceAdoptionItem::getTaskId, taskId)
                        .eq(ResourceAdoptionItem::getStatus, ResourceAdoptionItemStatus.FAILED.name())
                        .set(ResourceAdoptionItem::getStatus, ResourceAdoptionItemStatus.PENDING.name())
                        .set(ResourceAdoptionItem::getResourceId, null)
                        .set(ResourceAdoptionItem::getSourceHash, null)
                        .set(ResourceAdoptionItem::getSnapshotSize, null)
                        .set(ResourceAdoptionItem::getHashBaselined, false)
                        .set(ResourceAdoptionItem::getErrorMessage, null)
        );
        if (reset == 0) {
            throw new IllegalArgumentException("当前任务没有可重试的失败条目");
        }
        task.setStatus(ResourceAdoptionTaskStatus.PENDING.name());
        task.setFinishedAt(null);
        task.setErrorMessage(null);
        if (taskMapper.updateById(task) != 1) {
            throw new ConcurrentModificationException("历史接管任务重试状态已变化");
        }
        return task;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean prepareRecovery(String taskId) {
        ResourceAdoptionTask task = taskMapper.findByTaskIdForUpdate(taskId);
        if (task == null) {
            return false;
        }
        ResourceAdoptionTaskStatus status = ResourceAdoptionTaskStatus.valueOf(task.getStatus());
        if (status != ResourceAdoptionTaskStatus.PENDING
                && status != ResourceAdoptionTaskStatus.RUNNING) {
            return false;
        }
        itemMapper.update(
                null,
                Wrappers.<ResourceAdoptionItem>lambdaUpdate()
                        .eq(ResourceAdoptionItem::getTaskId, taskId)
                        .eq(ResourceAdoptionItem::getStatus, ResourceAdoptionItemStatus.READING.name())
                        .set(ResourceAdoptionItem::getStatus, ResourceAdoptionItemStatus.PENDING.name())
                        .set(ResourceAdoptionItem::getResourceId, null)
                        .set(ResourceAdoptionItem::getSourceHash, null)
                        .set(ResourceAdoptionItem::getSnapshotSize, null)
                        .set(ResourceAdoptionItem::getHashBaselined, false)
                        .set(ResourceAdoptionItem::getErrorMessage, null)
        );
        task.setStatus(ResourceAdoptionTaskStatus.PENDING.name());
        task.setFinishedAt(null);
        task.setErrorMessage(null);
        if (taskMapper.updateById(task) != 1) {
            throw new ConcurrentModificationException("历史接管任务恢复状态已变化");
        }
        return true;
    }

    public ResourceAdoptionTask findTask(String taskId) {
        ResourceAdoptionTask task = taskMapper.findByTaskId(taskId);
        if (task == null) {
            throw new IllegalArgumentException("历史接管任务不存在");
        }
        return task;
    }

    public List<String> recoverableTaskIds() {
        return taskMapper.findRecoverableTaskIds();
    }

    private int count(List<ResourceAdoptionItem> items, ResourceAdoptionItemStatus status) {
        return (int) items.stream().filter(item -> status.name().equals(item.getStatus())).count();
    }

    private ResourceAdoptionTaskStatus resolveTerminalStatus(int adopted, int failed) {
        if (failed == 0) {
            return ResourceAdoptionTaskStatus.SUCCESS;
        }
        return adopted > 0
                ? ResourceAdoptionTaskStatus.PARTIAL_SUCCESS
                : ResourceAdoptionTaskStatus.FAILED;
    }

    private String truncate(String value) {
        String normalized = StringUtils.hasText(value) ? value : "历史资源接管失败";
        return normalized.length() <= 1024 ? normalized : normalized.substring(0, 1024);
    }

    public record ClaimedItem(ResourceAdoptionTask task, ResourceAdoptionItem item) {
    }
}