package com.ld.poetry.service.prerender;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.StructuredTaskScope;

@Component
@RequiredArgsConstructor
@Slf4j
class PrerenderExecutor {

    private final PrerenderService prerenderService;
    private final PrerenderNodeRendererRegistry rendererRegistry;

    void execute(PrerenderRequest request, PrerenderPlan plan, PrerenderSnapshot snapshot) {
        if (request.clearTemplateCache()) {
            prerenderService.clearTemplateCache();
        }

        executeCleanups(request.cleanups());

        if (plan.renderNodes().isEmpty()) {
            log.info("预渲染计划无需写入页面: {}", plan.description());
            return;
        }

        TreeMap<Integer, List<PrerenderNode>> buckets = new TreeMap<>();
        for (PrerenderNode node : plan.renderNodes()) {
            buckets.computeIfAbsent(node.priority(), key -> new ArrayList<>()).add(node);
        }

        for (Map.Entry<Integer, List<PrerenderNode>> entry : buckets.entrySet()) {
            renderBatch(plan.description(), entry.getKey(), entry.getValue(), snapshot);
        }
    }

    private void executeCleanups(List<PrerenderCleanup> cleanups) {
        for (PrerenderCleanup cleanup : cleanups) {
            switch (cleanup.type()) {
                case ARTICLE -> prerenderService.deleteArticle(cleanup.articleId());
                case CATEGORY_PAGE -> prerenderService.deleteCategoryPage(cleanup.sortId(), cleanup.labelId());
                case PAGE -> prerenderService.deletePage(cleanup.pageType());
                case SORT_INDEX -> prerenderService.deleteSortIndexPage();
            }
        }
    }

    private void renderBatch(String description, int priority, List<PrerenderNode> nodes, PrerenderSnapshot snapshot) {
        try (var scope = StructuredTaskScope.open()) {
            log.info("开始执行预渲染批次: {}, priority={}, pages={}", description, priority, nodes.size());
            List<StructuredTaskScope.Subtask<Void>> subtasks = new ArrayList<>();
            for (PrerenderNode node : nodes) {
                subtasks.add(scope.fork(() -> rendererRegistry.render(node, snapshot)));
            }
            scope.join();
            ensureSuccessfulTasks(subtasks, description, priority);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("预渲染批次被中断: " + description + ", priority=" + priority, e);
        }
    }

    private void ensureSuccessfulTasks(List<? extends StructuredTaskScope.Subtask<?>> subtasks, String description, int priority) {
        for (StructuredTaskScope.Subtask<?> subtask : subtasks) {
            if (subtask.state() == StructuredTaskScope.Subtask.State.FAILED) {
                throw new IllegalStateException("预渲染批次失败: " + description + ", priority=" + priority, subtask.exception());
            }
        }
    }
}
