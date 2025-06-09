package com.ld.poetry.controller;

import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.service.AsyncImageCompressService;
import com.ld.poetry.utils.image.ImageCompressUtil;
import com.ld.poetry.vo.FileVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.CompletableFuture;

/**
 * 图片压缩管理控制器
 * 提供图片压缩统计、批量操作等功能
 */
@RestController
@RequestMapping("/imageCompress")
@Slf4j
public class ImageCompressController {

    @Autowired
    private AsyncImageCompressService asyncImageCompressService;

    /**
     * 获取图片压缩统计信息
     */
    @GetMapping("/stats")
    @LoginCheck(0)
    public PoetryResult<AsyncImageCompressService.CompressStats> getCompressStats() {
        AsyncImageCompressService.CompressStats stats = asyncImageCompressService.getCompressStats();
        return PoetryResult.success(stats);
    }

    /**
     * 重置压缩统计信息
     */
    @PostMapping("/resetStats")
    @LoginCheck(0)
    public PoetryResult resetStats() {
        asyncImageCompressService.resetStats();
        return PoetryResult.success("统计信息已重置");
    }

    /**
     * 测试图片压缩效果
     */
    @PostMapping("/testCompress")
    @LoginCheck
    public PoetryResult<Object> testCompress(@RequestParam("file") MultipartFile file) {
        try {
            if (!isImageFile(file.getContentType())) {
                return PoetryResult.fail("请上传图片文件！");
            }

            log.info("开始测试压缩: {}", file.getOriginalFilename());

            ImageCompressUtil.CompressResult result = ImageCompressUtil.smartCompress(file);

            return PoetryResult.success(new Object() {
                public final String originalFilename = file.getOriginalFilename();
                public final String contentType = result.getContentType();
                public final long originalSize = result.getOriginalSize();
                public final long compressedSize = result.getCompressedSize();
                public final double compressionRatio = result.getCompressionRatio();
                public final String originalSizeDisplay = formatFileSize(result.getOriginalSize());
                public final String compressedSizeDisplay = formatFileSize(result.getCompressedSize());
                public final String savedSizeDisplay = formatFileSize(result.getOriginalSize() - result.getCompressedSize());
            });

        } catch (Exception e) {
            log.error("测试压缩失败: {}", e.getMessage(), e);
            return PoetryResult.fail("测试压缩失败: " + e.getMessage());
        }
    }

    /**
     * 异步压缩单个图片
     */
    @PostMapping("/compressAsync")
    @LoginCheck
    public PoetryResult<String> compressAsync(@RequestParam("file") MultipartFile file) {
        try {
            if (!isImageFile(file.getContentType())) {
                return PoetryResult.fail("请上传图片文件！");
            }

            CompletableFuture<ImageCompressUtil.CompressResult> future = 
                    asyncImageCompressService.compressImageAsync(file);

            return PoetryResult.success("图片压缩任务已提交，正在后台处理...");

        } catch (Exception e) {
            log.error("异步压缩提交失败: {}", e.getMessage(), e);
            return PoetryResult.fail("异步压缩提交失败: " + e.getMessage());
        }
    }

    /**
     * 批量压缩图片
     */
    @PostMapping("/batchCompress")
    @LoginCheck(0)
    public PoetryResult<String> batchCompress(@RequestParam("files") MultipartFile[] files) {
        try {
            if (files == null || files.length == 0) {
                return PoetryResult.fail("请选择要压缩的图片文件！");
            }

            log.info("开始批量压缩 {} 个文件", files.length);

            CompletableFuture<AsyncImageCompressService.BatchCompressResult> future = 
                    asyncImageCompressService.batchCompressAsync(files);

            return PoetryResult.success("批量压缩任务已提交，正在后台处理 " + files.length + " 个文件...");

        } catch (Exception e) {
            log.error("批量压缩提交失败: {}", e.getMessage(), e);
            return PoetryResult.fail("批量压缩提交失败: " + e.getMessage());
        }
    }

    /**
     * 自定义参数压缩测试
     */
    @PostMapping("/testCustomCompress")
    @LoginCheck
    public PoetryResult<Object> testCustomCompress(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "maxWidth", defaultValue = "1920") int maxWidth,
            @RequestParam(value = "maxHeight", defaultValue = "1080") int maxHeight,
            @RequestParam(value = "quality", defaultValue = "0.85") float quality,
            @RequestParam(value = "targetSize", defaultValue = "512000") long targetSize) {

        try {
            if (!isImageFile(file.getContentType())) {
                return PoetryResult.fail("请上传图片文件！");
            }

            log.info("开始自定义参数压缩测试: {}, 目标尺寸: {}x{}, 质量: {}, 目标大小: {}KB", 
                    file.getOriginalFilename(), maxWidth, maxHeight, quality, targetSize / 1024);

            ImageCompressUtil.CompressResult result = 
                    ImageCompressUtil.smartCompress(file, maxWidth, maxHeight, quality, targetSize);

            return PoetryResult.success(new Object() {
                public final String originalFilename = file.getOriginalFilename();
                public final String contentType = result.getContentType();
                public final long originalSize = result.getOriginalSize();
                public final long compressedSize = result.getCompressedSize();
                public final double compressionRatio = result.getCompressionRatio();
                public final String originalSizeDisplay = formatFileSize(result.getOriginalSize());
                public final String compressedSizeDisplay = formatFileSize(result.getCompressedSize());
                public final String savedSizeDisplay = formatFileSize(result.getOriginalSize() - result.getCompressedSize());
                public final int targetWidth = maxWidth;
                public final int targetHeight = maxHeight;
                public final float targetQuality = quality;
                public final String targetSizeDisplay = formatFileSize(targetSize);
            });

        } catch (Exception e) {
            log.error("自定义参数压缩测试失败: {}", e.getMessage(), e);
            return PoetryResult.fail("自定义参数压缩测试失败: " + e.getMessage());
        }
    }

    /**
     * 检查是否为图片文件
     */
    private boolean isImageFile(String contentType) {
        return contentType != null && (
                contentType.startsWith("image/jpeg") ||
                contentType.startsWith("image/jpg") ||
                contentType.startsWith("image/png") ||
                contentType.startsWith("image/gif") ||
                contentType.startsWith("image/bmp") ||
                contentType.startsWith("image/webp")
        );
    }

    /**
     * 格式化文件大小显示
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
} 