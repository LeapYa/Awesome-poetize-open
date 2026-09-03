package com.ld.poetry.service.prerender;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.StructuredTaskScope;

@Component
@RequiredArgsConstructor
@Slf4j
class PrerenderExecutor {

    private final PrerenderService prerenderService;
    private final PrerenderNodeRendererRegistry rendererRegistry;
    private final PluginBootstrapMaterializer pluginBootstrapMaterializer;

    /**
     * 渲染并发上限（配置项 prerender.max-concurrent-renders，默认 6）。
     * 2核/24连接的机器上数十路并发渲染会造成连接与线程资源的瞬时争抢，
     * 限流后整体变慢但稳定可完成（预渲染要求：必须完成，可以慢）。
     */
    @Value("${prerender.max-concurrent-renders:6}")
    private int maxConcurrentRenders;

    private Semaphore renderPermits;

    @PostConstruct
    void initRenderPermits() {
        this.renderPermits = new Semaphore(maxConcurrentRenders);
    }

    void execute(PrerenderRequest request, PrerenderPlan plan, PrerenderSnapshot snapshot) {
        // 预渲染前确保 index.html 中的 <!--PB_BOOTSTRAP--> 占位符已被物化为 pb.*.js script 引用
        // 已物化时直接返回，不重复写文件
        pluginBootstrapMaterializer.ensureMaterialized();

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
                case ARTICLE -> prerenderService.deleteArticle(cleanup.articleId(), cleanup.articleSlug());
                case CATEGORY_PAGE -> prerenderService.deleteCategoryPage(cleanup.sortId(), cleanup.labelId());
                case PAGE -> prerenderService.deletePage(cleanup.pageType());
                case SORT_INDEX -> prerenderService.deleteSortIndexPage();
            }
        }
    }

    private void renderBatch(String description, int priority, List<PrerenderNode> nodes, PrerenderSnapshot snapshot) {
        // awaitAll：单个页面渲染失败不取消其余页面，先把能渲染的全部渲染完；批次末尾遇首个失败即抛出
        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAll())) {
            log.info("开始执行预渲染批次: {}, priority={}, pages={}", description, priority, nodes.size());
            List<StructuredTaskScope.Subtask<Void>> subtasks = new ArrayList<>();
            for (PrerenderNode node : nodes) {
                subtasks.add(scope.fork(() -> {
                    try {
                        renderPermits.acquire();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("等待渲染许可被中断: " + node.key(), e);
                    }
                    try {
                        rendererRegistry.render(node, snapshot);
                    } finally {
                        renderPermits.release();
                    }
                }));
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
