package com.ld.poetry.config;

import com.ld.poetry.service.prerender.PrerenderAssetConsistencyChecker;
import com.ld.poetry.service.prerender.PrerenderAssetConsistencyChecker.ConsistencyResult;
import com.ld.poetry.service.prerender.PrerenderFacade;
import com.ld.poetry.service.prerender.PrerenderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 启动时预渲染 Runner
 *
 * <p>
 * 应用启动后异步预渲染主要页面、分类页面和文章页面，
 * 保证搜索引擎爬虫和社交媒体抓取时能获取完整的 HTML。
 *
 * <p>
 * 启动时先校验预渲染 HTML 与构建产物的一致性：
 * 不一致则同步重渲染立即修复（消除爬虫访问到引用不存在资源的窗口期），
 * 一致则走原有延迟异步重渲染流程。
 *
 * <p>
 * 从 {@link PoetryApplicationRunner} 拆分出来，遵循单一职责原则。
 *
 * @author LeapYa
 * @since 2026-03-05
 */
@Component
@Order(30)
@Slf4j
public class PrerenderStartupRunner implements ApplicationRunner {

    @Autowired
    private PrerenderFacade prerenderFacade;

    @Autowired
    private PrerenderService prerenderService;

    @Autowired
    private PrerenderAssetConsistencyChecker consistencyChecker;

    @Value("${prerender.startup.enabled:true}")
    private boolean prerenderStartupEnabled;

    @Value("${prerender.startup.delay:10}")
    private int prerenderStartupDelay;

    @Override
    public void run(ApplicationArguments args) {
        if (!prerenderStartupEnabled) {
            log.info("启动时预渲染已禁用，跳过预渲染任务");
            return;
        }

        if (!prerenderService.isTemplateAvailable()) {
            log.warn("SPA 模板文件不存在，跳过预渲染任务");
            return;
        }

        if (rebuildImmediatelyIfInconsistent()) {
            return;
        }

        log.info("启动时预渲染已启用，将在 {} 秒后开始执行", prerenderStartupDelay);
        prerenderFacade.rebuildSiteAsync(Duration.ofSeconds(prerenderStartupDelay));
    }

    /**
     * 校验预渲染 HTML 与构建产物一致性，不一致时同步重渲染立即修复。
     *
     * @return true 表示已同步重渲染（跳过延迟异步流程）；false 表示一致性正常，继续走延迟异步重渲染
     */
    private boolean rebuildImmediatelyIfInconsistent() {
        ConsistencyResult result = consistencyChecker.check();
        if (result.isConsistent()) {
            return false;
        }
        log.warn("检测到预渲染 HTML 引用了 {} 个不存在的静态资源，立即同步重渲染修复", result.missingAssets().size());
        try {
            prerenderFacade.rebuildSite();
            log.info("预渲染 HTML 已通过同步重渲染修复");
            return true;
        } catch (Exception e) {
            log.error("同步重渲染修复失败，回退到延迟异步重渲染", e);
            return false;
        }
    }
}
