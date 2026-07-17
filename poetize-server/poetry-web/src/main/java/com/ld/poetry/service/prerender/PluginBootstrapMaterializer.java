package com.ld.poetry.service.prerender;

import com.ld.poetry.service.PluginBootstrapDataProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 插件配置物化为静态 JS 文件。
 *
 * <p>把首屏插件聚合数据写入 {@code window.__PB__=<JSON>;} 形式的
 * 带 hash 的 JS 文件（{@code pb.<sha256-12>.js}），并更新 index.html 中的
 * {@code <script>} 引用。这样 CDN 可以对该 JS 永久缓存，index.html 不缓存以即时生效。
 *
 * <p>复用项目现有的"后端写共享卷"模式（参考 {@link PrerenderEngine#writePage}）。
 * 所有物化操作失败只 log.warn，绝不影响插件增删改主流程。
 *
 * <p>命名说明：前台暴露的文件名/变量名使用中性缩写 pb（避免品牌名泄露），
 * 后端配置项仍保留 poetize.plugin-bootstrap 前缀（不暴露到前台）。
 *
 * @author LeapYa
 * @since 2026-07-16
 */
@Service
@Slf4j
public class PluginBootstrapMaterializer {

    private static final String FILE_PREFIX = "pb.";
    private static final String FILE_SUFFIX = ".js";
    private static final String PLACEHOLDER = "<!--PB_BOOTSTRAP-->";
    private static final Pattern SCRIPT_PATTERN = Pattern.compile(
            "<script\\s+[^>]*src=\"[^\"]*pb\\.[^\"]*\\.js\"[^>]*></script>\\s*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern APP_DIV_PATTERN = Pattern.compile(
            "<div\\s+id\\s*=\\s*[\"']app[\"']", Pattern.CASE_INSENSITIVE);
    // 限定为 /pb.<hex>.js 形式，避免误匹配 HTML 注释里的 pb.[hash].js 字面量
    private static final Pattern ANY_PLUGIN_SCRIPT_PATTERN = Pattern.compile(
            "/pb\\.[0-9a-f]+\\.js", Pattern.CASE_INSENSITIVE);
    private static final int KEEP_FILE_COUNT = 100;

    private final PluginBootstrapDataProvider dataProvider;
    private final JsonMapper objectMapper;

    /**
     * 单实例本地锁，覆盖单 jar 部署；多实例分布式部署需要替换为 Redis 分布式锁
     * 以避免并发写同一 index.html / 旧文件被误删。
     */
    private final ReentrantLock lock = new ReentrantLock();

    @Value("${prerender.template-path:/app/web-dist/index.html}")
    private String templatePath;

    @Value("${poetize.plugin-bootstrap.output-dir:/app/web-dist/static}")
    private String outputDir;

    @Value("${poetize.plugin-bootstrap.enabled:true}")
    private boolean enabled;

    public PluginBootstrapMaterializer(PluginBootstrapDataProvider dataProvider, JsonMapper objectMapper) {
        this.dataProvider = dataProvider;
        this.objectMapper = objectMapper;
    }

    /**
     * 同步物化：构建聚合数据 → 序列化为 JS → 写文件（带 hash）→ 更新 index.html → 清理旧文件。
     * 失败只 log.warn，不抛异常。
     */
    public void materialize() {
        if (!enabled) {
            return;
        }
        lock.lock();
        try {
            Map<String, Object> data = dataProvider.buildBootstrapData();
            String json = objectMapper.writeValueAsString(data);
            String jsContent = "window.__PB__=" + json + ";";
            String hash = sha256Hex12(jsContent.getBytes(StandardCharsets.UTF_8));
            String fileName = FILE_PREFIX + hash + FILE_SUFFIX;

            Path outputDirPath = Path.of(outputDir);
            Files.createDirectories(outputDirPath);
            Path targetFile = outputDirPath.resolve(fileName);
            if (!Files.exists(targetFile)) {
                atomicWrite(targetFile, jsContent);
            }

            updateIndexHtml(fileName);
            cleanupOldFiles(fileName);
        } catch (Exception e) {
            log.warn("插件配置物化失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 异步物化：用虚拟线程提交 {@link #materialize()}，异常只 log.warn 不抛出。
     */
    public void materializeAsync() {
        Thread.ofVirtual().name("plugin-bootstrap-materializer").start(() -> {
            try {
                materialize();
            } catch (Exception e) {
                log.warn("异步物化插件配置失败: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * 启动钩子调用：检测 index.html 是否需要重新物化。
     * 若 index.html 不含 plugin-bootstrap.*.js 的 script 引用（首次启动或被构建覆盖），
     * 则调用 {@link #materialize()}；否则跳过。
     */
    public void ensureMaterialized() {
        if (!enabled) {
            return;
        }
        try {
            Path template = Path.of(templatePath);
            if (!Files.isRegularFile(template)) {
                log.warn("index.html 不存在，跳过插件配置物化检查: {}", templatePath);
                return;
            }
            String html = Files.readString(template, StandardCharsets.UTF_8);
            if (ANY_PLUGIN_SCRIPT_PATTERN.matcher(html).find()) {
                return;
            }
            materialize();
        } catch (Exception e) {
            log.warn("启动钩子检查插件配置物化失败: {}", e.getMessage(), e);
        }
    }

    private void updateIndexHtml(String currentFileName) {
        try {
            Path template = Path.of(templatePath);
            if (!Files.isRegularFile(template)) {
                log.warn("index.html 模板不存在，跳过更新: {}", templatePath);
                return;
            }
            String html = Files.readString(template, StandardCharsets.UTF_8);
            String scriptTag = "<script src=\"/static/" + currentFileName + "\"></script>";
            String newHtml;
            if (html.contains(PLACEHOLDER)) {
                newHtml = html.replace(PLACEHOLDER, scriptTag);
            } else {
                Matcher matcher = SCRIPT_PATTERN.matcher(html);
                if (matcher.find()) {
                    newHtml = matcher.replaceFirst(Matcher.quoteReplacement(scriptTag));
                } else {
                    newHtml = insertBeforeAppDiv(html, scriptTag);
                }
            }
            if (!newHtml.equals(html)) {
                atomicWrite(template, newHtml);
            }
        } catch (Exception e) {
            log.warn("更新 index.html 中的插件 bootstrap 引用失败: {}", e.getMessage(), e);
        }
    }

    private String insertBeforeAppDiv(String html, String scriptTag) {
        Matcher matcher = APP_DIV_PATTERN.matcher(html);
        if (!matcher.find()) {
            return html;
        }
        int position = matcher.start();
        return html.substring(0, position) + scriptTag + "\n" + html.substring(position);
    }

    /**
     * 旧文件清理（软上限策略）。
     *
     * <p>默认不主动清理，保留全部历史 hash 文件，确保任何旧引用（浏览器缓存、CDN 边缘节点、
     * 预渲染 HTML 固化的引用）都能命中，永不 404。插件变更是管理员级低频操作，每次 +8KB，
     * 一年切换 1000 次也就 8MB，磁盘增长可忽略。
     *
     * <p>仅当历史文件超过 {@link #KEEP_FILE_COUNT}（100）时才删除最旧的，作为极端情况兜底。
     */
    private void cleanupOldFiles(String keepFileName) {
        Path outputDirPath = Path.of(outputDir);
        if (!Files.isDirectory(outputDirPath)) {
            return;
        }
        try (Stream<Path> stream = Files.list(outputDirPath)) {
            List<Path> files = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith(FILE_PREFIX) && name.endsWith(FILE_SUFFIX);
                    })
                    .sorted((a, b) -> Long.compare(lastModifiedSafe(b), lastModifiedSafe(a)))
                    .toList();
            if (files.size() <= KEEP_FILE_COUNT) {
                return;
            }
            for (int i = KEEP_FILE_COUNT; i < files.size(); i++) {
                Path file = files.get(i);
                String name = file.getFileName().toString();
                if (name.equals(keepFileName)) {
                    continue;
                }
                try {
                    Files.deleteIfExists(file);
                } catch (IOException e) {
                    log.warn("删除旧插件 bootstrap 文件失败: {}: {}", file, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("清理旧插件 bootstrap 文件失败: {}", e.getMessage(), e);
        }
    }

    private long lastModifiedSafe(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    private void atomicWrite(Path target, String content) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private String sha256Hex12(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(bytes);
        String fullHex = HexFormat.of().formatHex(hashBytes);
        return fullHex.substring(0, 12);
    }
}
