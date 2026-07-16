package com.ld.poetry.config;

import com.ld.poetry.service.prerender.PluginBootstrapMaterializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时检查插件配置物化 Runner。
 *
 * <p>应用启动后异步延迟检查 index.html 是否需要重新物化插件 bootstrap JS
 * （首次启动或构建覆盖后会缺失 script 引用），保证首屏访问即可命中静态 JS。
 *
 * <p>从 {@link PoetryApplicationRunner} 拆分出来的独立职责 Runner，参考
 * {@link PrerenderStartupRunner} 的异步模式。
 *
 * @author LeapYa
 * @since 2026-07-16
 */
@Component
@Order(40)
@Slf4j
public class PluginBootstrapStartupRunner implements ApplicationRunner {

    @Autowired
    private PluginBootstrapMaterializer materializer;

    @Value("${poetize.plugin-bootstrap.enabled:true}")
    private boolean enabled;

    @Value("${poetize.plugin-bootstrap.startup-delay:15}")
    private int startupDelay;

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("插件配置物化已禁用，跳过启动钩子");
            return;
        }
        log.info("插件配置物化启动钩子已启用，将在 {} 秒后检查", startupDelay);
        Thread.ofVirtual().name("plugin-bootstrap-startup").start(() -> {
            try {
                if (startupDelay > 0) {
                    Thread.sleep(startupDelay * 1000L);
                }
                materializer.ensureMaterialized();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("插件配置物化启动钩子被中断");
            } catch (Exception e) {
                log.warn("插件配置物化启动钩子执行失败: {}", e.getMessage(), e);
            }
        });
    }
}
