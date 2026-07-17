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
 * <p>在 PrerenderStartupRunner 之前同步执行 ensureMaterialized()，
 * 把 /static/pb.[hash].js 物化到磁盘并把 index.html 中的占位符替换为 <script> 引用。
 * 确保后续预渲染读取的模板已含 pb.js script 引用，避免预渲染 HTML 残留占位符。
 *
 * @author LeapYa
 * @since 2026-07-16
 */
@Component
@Order(25)
@Slf4j
public class PluginBootstrapStartupRunner implements ApplicationRunner {

    @Autowired
    private PluginBootstrapMaterializer materializer;

    @Value("${poetize.plugin-bootstrap.enabled:true}")
    private boolean enabled;

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("插件配置物化已禁用，跳过启动钩子");
            return;
        }
        materializer.ensureMaterialized();
    }
}
