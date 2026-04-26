package com.ld.poetry.event;

import com.ld.poetry.service.SitemapService;
import com.ld.poetry.service.SeoService;
import com.ld.poetry.service.ArticleService;
import com.ld.poetry.service.prerender.PrerenderFacade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 文章事件监听器
 * 在数据库事务提交后执行预渲染操作
 */
@Component
@Slf4j
public class ArticleEventListener {

    private static final long DEDUP_WINDOW_MILLIS = 200L;
    
    @Autowired
    private PrerenderFacade prerenderFacade;
    
    @Autowired
    private SitemapService sitemapService;
    
    @Autowired
    private SeoService seoService;
    
    @org.springframework.context.annotation.Lazy
    @Autowired
    private ArticleService articleService;
    
    // 用于去重的延迟调度器（使用虚拟线程工厂）
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, 
        Thread.ofVirtual().name("article-event-scheduler-", 0).factory());
    
    // 记录待处理的预渲染任务，用于去重
    private final ConcurrentHashMap<Integer, Runnable> pendingRenderTasks = new ConcurrentHashMap<>();
    
    /**
     * 监听文章保存事件，在事务提交后执行预渲染
     * 
     * 注意：使用fallbackExecution=true确保即使在非事务上下文（如虚拟线程）中发布事件也能触发
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Async
    public void handleArticleSavedEvent(ArticleSavedEvent event) {
        log.info("收到文章事件: ID={}, 操作={}, 可见={}, 分类ID={}, 提交搜索引擎={}", 
                 event.getArticleId(), event.getOperationType(), event.getViewStatus(), 
                 event.getSortId(), event.getSubmitToSearchEngine());
        
        try {
            switch (event.getOperationType()) {
                case "CREATE":
                    if (Boolean.TRUE.equals(event.getViewStatus())) {
                        scheduleRenderWithDeduplication(event);
                        log.info("已安排新文章预渲染任务: ID={}", event.getArticleId());
                        updateSitemapAsync(event.getArticleId(), "CREATE");
                    } else {
                        log.info("新建文章不可见，跳过预渲染: ID={}", event.getArticleId());
                    }
                    break;
                    
                case "UPDATE":
                    if (Boolean.TRUE.equals(event.getViewStatus())) {
                        scheduleRenderWithDeduplication(event);
                        log.info("已安排文章更新预渲染任务: ID={}", event.getArticleId());
                    } else {
                        scheduleRenderWithDeduplication(event);
                        log.info("已安排文章预渲染删除任务: ID={}", event.getArticleId());
                    }
                    updateSitemapAsync(event.getArticleId(), "UPDATE");
                    break;
                    
                case "DELETE":
                    scheduleRenderWithDeduplication(event);
                    log.info("已安排文章删除预渲染任务: ID={}, 分类ID={}", event.getArticleId(), event.getSortId());
                    updateSitemapAsync(event.getArticleId(), "DELETE");
                    break;
                    
                default:
                    log.warn("未知的文章操作类型: {}", event.getOperationType());
            }
        } catch (Exception e) {
            log.warn("文章预渲染任务安排失败: ID={}, 操作={}, 错误={}", 
                     event.getArticleId(), event.getOperationType(), e.getMessage());
        }
    }
    
    /**
     * 去重调度预渲染任务
     * 如果短时间内同一文章有多次渲染请求，只执行最后一次
     */
    private void scheduleRenderWithDeduplication(ArticleSavedEvent event) {
        Integer articleId = event.getArticleId();
        AtomicReference<Runnable> taskReference = new AtomicReference<>();
        Runnable renderTask = () -> {
            Runnable latestTask = pendingRenderTasks.get(articleId);
            if (latestTask != taskReference.get()) {
                log.info("跳过过期的文章预渲染任务: ID={}", articleId);
                return;
            }

            pendingRenderTasks.remove(articleId, taskReference.get());

            try {
                prerenderFacade.handleArticleEvent(event);

                if (shouldSubmitSeo(event)) {
                    performSeoSubmission(articleId, event.getOperationType(), event.getTaskId());
                }
            } catch (Exception e) {
                log.warn("预渲染执行失败: ID={}, 操作={}, 错误={}", 
                         articleId, event.getOperationType(), e.getMessage());
            }
        };
        taskReference.set(renderTask);
        
        // 如果已经有待处理的任务，先取消之前的任务
        Runnable previousTask = pendingRenderTasks.put(articleId, renderTask);
        if (previousTask != null) {
            log.info("发现重复预渲染任务，已取消前一个任务: ID={}", articleId);
        }
        
        // 给极短时间内的连续保存一个合并窗口，只执行最后一次任务。
        scheduler.schedule(renderTask, DEDUP_WINDOW_MILLIS, TimeUnit.MILLISECONDS);
    }

    private boolean shouldSubmitSeo(ArticleSavedEvent event) {
        return event != null
                && Boolean.TRUE.equals(event.getSubmitToSearchEngine())
                && Boolean.TRUE.equals(event.getViewStatus())
                && ("CREATE".equals(event.getOperationType()) || "UPDATE".equals(event.getOperationType()));
    }
    
    /**
     * 异步更新sitemap缓存（文章变更时只清除缓存，不推送）
     * 
     * @param articleId 文章ID
     * @param operation 操作类型
     */
    private void updateSitemapAsync(Integer articleId, String operation) {
        try {
            log.info("文章{}操作，清除sitemap缓存: ID={}", operation, articleId);
            sitemapService.updateArticleSitemap(articleId);
        } catch (Exception e) {
            log.warn("清除sitemap缓存失败: ID={}, 操作={}, 错误={}", 
                     articleId, operation, e.getMessage());
        }
    }
    
    /**
     * 执行SEO推送（预渲染完成后立即执行）
     * 向搜索引擎推送新增/更新的文章并记录状态
     * 
     * @param articleId 文章ID
     * @param operation 操作类型
     * @param taskId 异步任务ID（如果有）
     */
    private void performSeoSubmission(Integer articleId, String operation, String taskId) {
        try {
            if (taskId != null) {
                articleService.updateSeoPushStatus(taskId, "pushing", "正在推送到搜索引擎...");
            }
            
            Map<String, Object> result = seoService.submitToSearchEngines(articleId);
            String status = (String) result.get("status");
            String message = (String) result.get("message");
            
            // 写入任务状态（让前端SSE流可以感知到SEO推送结果）
            if (taskId != null) {
                // 如果是"skipped"或"disabled"，前端一般显示为完成状态（无需红色警告）
                // 我们在 ArticleServiceImpl 中写回
                String pushStatus = "pushed".equals(status) ? "success" 
                                  : "skipped".equals(status) || "disabled".equals(status) ? "skipped"
                                  : "failed";
                articleService.updateSeoPushStatus(taskId, pushStatus, message);
            }
            
            // 根据不同状态输出不同的日志
            switch (status) {
                case "pushed":
                    log.info("搜索引擎推送完成，文章ID: {}, {}", articleId, message);
                    break;
                case "skipped":
                    log.info("搜索引擎推送跳过，文章ID: {}, 原因: {}", articleId, message);
                    break;
                case "disabled":
                    break;
                case "failed":
                    log.warn("搜索引擎推送失败，文章ID: {}, {}", articleId, message);
                    break;
                case "error":
                    log.error("搜索引擎推送错误，文章ID: {}, {}", articleId, message);
                    if (taskId != null) {
                        articleService.updateSeoPushStatus(taskId, "failed", message);
                    }
                    break;
                default:
                    log.info("搜索引擎推送完成，文章ID: {}, 状态: {}", articleId, status);
            }
        } catch (Exception e) {
            log.error("搜索引擎推送异常，文章ID: {}, 错误: {}", articleId, e.getMessage(), e);
            if (taskId != null) {
                articleService.updateSeoPushStatus(taskId, "failed", "推送执行异常: " + e.getMessage());
            }
        }
    }
    
}
