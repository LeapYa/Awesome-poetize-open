package com.ld.poetry.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ld.poetry.controller.dto.ResourceAdoptionPreview;
import com.ld.poetry.controller.dto.ResourceAdoptionRequest;
import com.ld.poetry.controller.dto.ResourceAdoptionTaskView;
import com.ld.poetry.dao.ResourceAdoptionItemMapper;
import com.ld.poetry.dao.ResourceAliasMapper;
import com.ld.poetry.entity.ResourceAdoptionItem;
import com.ld.poetry.entity.ResourceAdoptionTask;
import com.ld.poetry.enums.ResourceAdoptionTaskStatus;
import com.ld.poetry.utils.storage.StorageSnapshot;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceAdoptionService {

    private static final int MAX_TASK_CANDIDATES = 5000;
    private static final int TASK_ITEM_VIEW_LIMIT = 500;

    private final ResourceReferenceService referenceService;
    private final ResourceAdoptionSourceResolver sourceResolver;
    private final ResourceAdoptionTaskStore taskStore;
    private final ResourceAdoptionItemMapper itemMapper;
    private final ResourceAliasMapper aliasMapper;
    private final ResourceStorageSnapshotService snapshotService;
    private final ResourceAdoptionCommitService commitService;
    private final ResourceMigrationCacheService cacheService;

    private final ExecutorService executor = Executors.newFixedThreadPool(
            2,
            Thread.ofVirtual().name("resource-adoption-", 0).factory()
    );
    private final Set<String> submittedTasks = ConcurrentHashMap.newKeySet();

    @PostConstruct
    public void recoverTasks() {
        try {
            for (String taskId : taskStore.recoverableTaskIds()) {
                if (taskStore.prepareRecovery(taskId)) {
                    submit(taskId);
                }
            }
        } catch (Exception e) {
            log.info("历史接管表尚未就绪或恢复失败，将等待数据库升级后由管理员重试: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    public ResourceAdoptionPreview preview(ResourceAdoptionRequest request) {
        List<ResourceAdoptionCandidate> candidates = resolveCandidates(request);
        int trusted = (int) candidates.stream().filter(ResourceAdoptionCandidate::trusted).count();
        int references = candidates.stream().mapToInt(ResourceAdoptionCandidate::referenceCount).sum();
        return new ResourceAdoptionPreview(
                candidates.size(),
                trusted,
                candidates.size() - trusted,
                references,
                candidates
        );
    }

    public ResourceAdoptionTask create(ResourceAdoptionRequest request, Integer createdBy) {
        List<ResourceAdoptionCandidate> candidates = resolveCandidates(request);
        ResourceAdoptionTask task = taskStore.createTask(createdBy, candidates);
        submit(task.getTaskId());
        return task;
    }

    public ResourceAdoptionTaskView getTask(String taskId) {
        ResourceAdoptionTask task = taskStore.refreshProgress(requireTaskId(taskId));
        List<ResourceAdoptionItem> items = itemMapper.selectList(
                Wrappers.<ResourceAdoptionItem>lambdaQuery()
                        .eq(ResourceAdoptionItem::getTaskId, taskId)
                        .orderByAsc(ResourceAdoptionItem::getStatus)
                        .orderByAsc(ResourceAdoptionItem::getId)
                        .last("limit " + TASK_ITEM_VIEW_LIMIT)
        );
        return new ResourceAdoptionTaskView(
                task,
                items,
                task.getTotalCount() != null && task.getTotalCount() > items.size()
        );
    }

    public boolean cancel(String taskId) {
        return taskStore.cancel(requireTaskId(taskId));
    }

    public ResourceAdoptionTask retry(String taskId) {
        ResourceAdoptionTask task = taskStore.prepareRetry(requireTaskId(taskId));
        submit(taskId);
        return task;
    }

    private List<ResourceAdoptionCandidate> resolveCandidates(ResourceAdoptionRequest request) {
        List<ResourceReferenceService.ReferenceCandidate> discovered = referenceService.scanReferences();
        if (discovered.isEmpty()) {
            throw new IllegalArgumentException("白名单业务字段中没有发现可接管的历史资源URL");
        }
        Map<String, ResourceReferenceService.ReferenceCandidate> discoveredByUrl = new LinkedHashMap<>();
        discovered.forEach(candidate -> discoveredByUrl.put(candidate.sourceUrl(), candidate));

        List<ResourceReferenceService.ReferenceCandidate> selected;
        if (request == null || request.sourceUrls() == null || request.sourceUrls().isEmpty()) {
            selected = discovered;
        } else {
            if (request.sourceUrls().size() > MAX_TASK_CANDIDATES) {
                throw new IllegalArgumentException("单个历史接管任务候选过多");
            }
            selected = new ArrayList<>();
            Set<String> seen = ConcurrentHashMap.newKeySet();
            for (String sourceUrl : request.sourceUrls()) {
                String normalized = normalizeSelectedUrl(sourceUrl);
                if (!seen.add(normalized)) {
                    continue;
                }
                ResourceReferenceService.ReferenceCandidate candidate = discoveredByUrl.get(normalized);
                if (candidate == null) {
                    throw new IllegalArgumentException("所选历史URL已不在当前引用扫描结果中: " + normalized);
                }
                selected.add(candidate);
            }
        }
        if (selected.isEmpty()) {
            throw new IllegalArgumentException("没有选择历史接管候选");
        }
        if (selected.size() > MAX_TASK_CANDIDATES) {
            throw new IllegalArgumentException("单个历史接管任务最多处理 " + MAX_TASK_CANDIDATES + " 个候选");
        }

        return selected.stream().map(this::classify).toList();
    }

    private ResourceAdoptionCandidate classify(ResourceReferenceService.ReferenceCandidate candidate) {
        ResourceAdoptionSourceResolver.Inspection inspection = sourceResolver.inspect(candidate.sourceUrl());
        if (!inspection.trusted()) {
            return new ResourceAdoptionCandidate(
                    candidate.sourceUrl(),
                    candidate.referenceCount(),
                    "UNTRUSTED",
                    false,
                    null,
                    inspection.reason()
            );
        }
        boolean registered = aliasMapper.findActiveByAliasUrl(candidate.sourceUrl()) != null;
        return new ResourceAdoptionCandidate(
                candidate.sourceUrl(),
                candidate.referenceCount(),
                registered ? "REGISTERED" : "AUTO_ADOPTABLE",
                true,
                inspection.storeType(),
                ""
        );
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
        try {
            while (!Thread.currentThread().isInterrupted()) {
                ResourceAdoptionTaskStore.ClaimedItem claimed = taskStore.claimNext(taskId);
                if (claimed == null) {
                    break;
                }
                processItem(claimed);
            }
            ResourceAdoptionTask completed = taskStore.refreshProgress(taskId);
            if (completed.getSuccessCount() != null && completed.getSuccessCount() > 0) {
                cacheService.invalidateAfterMigration();
            }
        } catch (Exception e) {
            log.error("历史资源接管任务执行失败: taskId={}", taskId, e);
            try {
                taskStore.refreshProgress(taskId);
            } catch (Exception refreshError) {
                log.warn("历史接管任务进度收尾失败: taskId={}", taskId, refreshError);
            }
        }
    }

    private void processItem(ResourceAdoptionTaskStore.ClaimedItem claimed) {
        ResourceAdoptionItem item = claimed.item();
        try {
            ResourceAdoptionSourceResolver.ResolvedSource source = sourceResolver.resolve(item.getSourceUrl());
            try (StorageSnapshot snapshot = snapshotService.capture(source.storeService(), source.resourceRef())) {
                commitService.commit(new ResourceAdoptionCommitService.VerifiedAdoption(
                        item.getId(),
                        claimed.task().getCreatedBy(),
                        item.getSourceUrl(),
                        source.storeType(),
                        source.storageKey(),
                        source.accessPath(),
                        snapshot.originalName(),
                        snapshot.sha256(),
                        snapshot.size(),
                        snapshot.contentType(),
                        LocalDateTime.now()
                ));
            }
        } catch (Exception e) {
            log.warn("历史资源接管条目失败: itemId={}, source={}, err={}",
                    item.getId(), item.getSourceUrl(), e.getMessage());
            taskStore.markFailed(item.getId(), safeMessage(e));
        }
    }

    private String normalizeSelectedUrl(String sourceUrl) {
        if (!StringUtils.hasText(sourceUrl)) {
            throw new IllegalArgumentException("所选历史资源URL不能为空");
        }
        String normalized = sourceUrl.trim();
        int fragment = normalized.indexOf('#');
        if (fragment >= 0) {
            normalized = normalized.substring(0, fragment);
        }
        if (!StringUtils.hasText(normalized) || normalized.length() > 2048) {
            throw new IllegalArgumentException("所选历史资源URL不合法");
        }
        return normalized;
    }

    private String requireTaskId(String taskId) {
        if (!StringUtils.hasText(taskId) || !taskId.matches("[a-fA-F0-9]{32}")) {
            throw new IllegalArgumentException("历史接管任务ID不合法");
        }
        return taskId;
    }

    private String safeMessage(Exception e) {
        String message = e == null ? null : e.getMessage();
        if (!StringUtils.hasText(message)) {
            return "历史资源接管失败";
        }
        return message.length() <= 1024 ? message : message.substring(0, 1024);
    }
}