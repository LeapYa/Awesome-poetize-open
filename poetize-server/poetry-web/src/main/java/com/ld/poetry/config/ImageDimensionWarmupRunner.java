package com.ld.poetry.config;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ld.poetry.entity.Resource;
import com.ld.poetry.service.ResourceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 启动后后台自动补齐历史图片尺寸。
 *
 * <p>目标：即使旧文章从未被访问过，也能在后台渐进式完成图片宽高回填，
 * 避免目录跳转时因为懒加载图片无占位导致布局偏移。</p>
 */
@Component
@Order(40)
@Slf4j
public class ImageDimensionWarmupRunner implements ApplicationRunner {

    private final ResourceService resourceService;

    @Value("${image.dimension.warmup.enabled:true}")
    private boolean warmupEnabled;

    @Value("${image.dimension.warmup.delay-seconds:20}")
    private int warmupDelaySeconds;

    @Value("${image.dimension.warmup.batch-size:100}")
    private int warmupBatchSize;

    @Value("${local.uploadUrl:}")
    private String localUploadUrl;

    @Value("${local.downloadUrl:}")
    private String localDownloadUrl;

    public ImageDimensionWarmupRunner(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!warmupEnabled) {
            log.info("图片尺寸后台补齐已禁用，跳过历史图片扫描");
            return;
        }

        Thread.ofVirtual().name("image-dimension-warmup").start(() -> {
            try {
                Thread.sleep(Math.max(0, warmupDelaySeconds) * 1000L);
                warmupMissingDimensions();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("图片尺寸后台补齐任务被中断");
            } catch (Exception e) {
                log.warn("图片尺寸后台补齐任务执行失败，不影响应用启动", e);
            }
        });
    }

    private void warmupMissingDimensions() {
        int totalUpdated = 0;
        int round = 0;

        while (true) {
            round++;
            Page<Resource> page = new Page<>(1, Math.max(1, warmupBatchSize), false);
            resourceService.lambdaQuery()
                    .isNull(Resource::getWidth)
                    .eq(Resource::getStoreType, "local")
                    .isNotNull(Resource::getMimeType)
                    .likeRight(Resource::getMimeType, "image/")
                    .orderByAsc(Resource::getId)
                    .page(page);

            List<Resource> records = page.getRecords();
            if (records == null || records.isEmpty()) {
                log.info("图片尺寸后台补齐完成，共写入 {} 条历史图片记录", totalUpdated);
                return;
            }

            AtomicInteger roundUpdated = new AtomicInteger(0);

            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (Resource resource : records) {
                    executor.submit(() -> {
                        try {
                            int[] dims = readImageDimensionsFromPath(resource.getPath());
                            if (dims == null) {
                                return;
                            }

                            Resource update = new Resource();
                            update.setId(resource.getId());
                            update.setWidth(dims[0]);
                            update.setHeight(dims[1]);
                            if (resourceService.updateById(update)) {
                                roundUpdated.incrementAndGet();
                            }
                        } catch (Exception e) {
                            log.debug("历史图片尺寸补齐失败: id={}, path={}, err={}",
                                    resource.getId(), resource.getPath(), e.getMessage());
                        }
                    });
                }
            }

            totalUpdated += roundUpdated.get();

            log.info("图片尺寸后台补齐第 {} 轮完成：扫描 {} 条，成功写入 {} 条",
                    round, records.size(), roundUpdated.get());

            if (records.size() < warmupBatchSize) {
                log.info("图片尺寸后台补齐结束，共写入 {} 条历史图片记录", totalUpdated);
                return;
            }

            try {
                Thread.sleep(200L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("图片尺寸后台补齐任务在批次间隔时被中断");
                return;
            }
        }
    }

    private int[] readImageDimensionsFromPath(String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }

        try (InputStream inputStream = openImageStream(path)) {
            if (inputStream == null) {
                return null;
            }
            BufferedImage image = ImageIO.read(inputStream);
            if (image == null) {
                return null;
            }
            return new int[]{image.getWidth(), image.getHeight()};
        } catch (Exception e) {
            return null;
        }
    }

    private InputStream openImageStream(String path) throws Exception {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            HttpURLConnection conn = (HttpURLConnection) URI.create(path).toURL().openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "Poetize-ImageWarmup/1.0");
            return conn.getInputStream();
        }

        String localFilePath = resolveLocalFilePath(path);
        if (!StringUtils.hasText(localFilePath)) {
            return null;
        }

        File file = new File(localFilePath);
        if (!file.exists() || !file.isFile()) {
            return null;
        }
        return new FileInputStream(file);
    }

    private String resolveLocalFilePath(String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }

        if (StringUtils.hasText(localDownloadUrl) && path.startsWith(localDownloadUrl) && StringUtils.hasText(localUploadUrl)) {
            return path.replace(localDownloadUrl, localUploadUrl).replace("/", File.separator);
        }

        if (path.startsWith("/") && StringUtils.hasText(localUploadUrl)) {
            String normalizedBase = localUploadUrl.endsWith("/") || localUploadUrl.endsWith("\\")
                    ? localUploadUrl
                    : localUploadUrl + File.separator;
            String relative = path.substring(1).replace("/", File.separator);
            return normalizedBase + relative;
        }

        return path;
    }
}
