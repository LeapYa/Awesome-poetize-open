package com.ld.poetry.config;

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

        log.info("启动时预渲染已启用，将在 {} 秒后开始执行", prerenderStartupDelay);
        scheduleStartupRebuild();
    }

    private void scheduleStartupRebuild() {
        if (!prerenderService.isTemplateAvailable()) {
            log.warn("SPA 模板文件不存在，跳过预渲染任务");
            return;
        }
        prerenderFacade.rebuildSiteAsync(Duration.ofSeconds(prerenderStartupDelay));
    }
}
