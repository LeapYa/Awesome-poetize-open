package com.ld.poetry.service.prerender;

import com.ld.poetry.event.ArticleSavedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class PrerenderFacade {

    private final PrerenderSeedMapper seedMapper;
    private final PrerenderSnapshotFactory snapshotFactory;
    private final PrerenderPlanner planner;
    private final PrerenderExecutor executor;
    private final PrerenderService prerenderService;

    public void rebuildSite() {
        execute(seedMapper.forFullSiteRebuild());
    }

    public void rebuildSiteAsync(Duration delay) {
        executeAsync(seedMapper.forFullSiteRebuild(), delay);
    }

    public void refreshFriendsPage() {
        execute(seedMapper.forFriendsPage());
    }

    public void refreshFavoritesPage() {
        execute(seedMapper.forFavoritesPage());
    }

    public void refreshSortHierarchy(Integer sortId) {
        execute(seedMapper.forSortRefresh(sortId));
    }

    public void deleteSortHierarchy(Integer sortId) {
        execute(seedMapper.forSortDeletion(sortId));
    }

    public void refreshLabelHierarchy(Integer currentSortId, Integer labelId, Integer previousSortId) {
        execute(seedMapper.forLabelRefresh(currentSortId, labelId, previousSortId));
    }

    public void deleteLabelHierarchy(Integer sortId, Integer labelId) {
        execute(seedMapper.forLabelDeletion(sortId, labelId));
    }

    public void handleArticleEvent(ArticleSavedEvent event) {
        execute(seedMapper.forArticleEvent(event));
    }

    void execute(PrerenderRequest request) {
        if (request == null || request.isNoop()) {
            return;
        }

        PrerenderSnapshot snapshot = snapshotFactory.createSnapshot(request);
        PrerenderPlan plan = planner.plan(request, snapshot);

        if (!plan.renderNodes().isEmpty() && !prerenderService.isTemplateAvailable()) {
            throw new IllegalStateException("SPA 模板文件不存在，无法执行预渲染: " + request.description());
        }

        log.info("开始执行预渲染请求: {}, seeds={}, cleanups={}, renders={}",
                request.description(), request.seeds().size(), request.cleanups().size(), plan.renderNodes().size());
        executor.execute(request, plan, snapshot);
        log.info("预渲染请求执行完成: {}", request.description());
    }

    public void executeAsync(PrerenderRequest request, Duration delay) {
        if (request == null || request.isNoop()) {
            return;
        }

        long sleepMillis = delay == null ? 0L : Math.max(0L, delay.toMillis());
        Thread.ofVirtual().name("prerender-facade-virtual").start(() -> {
            try {
                if (sleepMillis > 0) {
                    Thread.sleep(sleepMillis);
                }
                execute(request);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("异步预渲染任务被中断: {}", request.description());
            } catch (Exception e) {
                log.error("异步预渲染任务失败: {}", request.description(), e);
            }
        });
    }
}
