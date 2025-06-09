package com.ld.poetry.service;

import com.ld.poetry.utils.image.ImageCompressUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 异步图片压缩服务
 * 使用虚拟线程处理大批量图片压缩任务
 */
@Service
@Slf4j
public class AsyncImageCompressService {

    // 统计信息
    private final AtomicLong totalProcessed = new AtomicLong(0);
    private final AtomicLong totalSaved = new AtomicLong(0);
    private final AtomicLong totalErrors = new AtomicLong(0);

    /**
     * 异步压缩单个图片
     */
    @Async("virtualThreadTaskExecutor")
    public CompletableFuture<ImageCompressUtil.CompressResult> compressImageAsync(MultipartFile file) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("开始异步压缩图片: {}", file.getOriginalFilename());
                
                ImageCompressUtil.CompressResult result = ImageCompressUtil.smartCompress(file);
                
                // 更新统计信息
                totalProcessed.incrementAndGet();
                totalSaved.addAndGet(result.getOriginalSize() - result.getCompressedSize());
                
                log.debug("图片压缩完成: {}, 节省空间: {}KB", 
                         file.getOriginalFilename(), 
                         (result.getOriginalSize() - result.getCompressedSize()) / 1024);
                
                return result;
                
            } catch (IOException e) {
                totalErrors.incrementAndGet();
                log.error("图片压缩失败: {}, 错误: {}", file.getOriginalFilename(), e.getMessage());
                throw new RuntimeException("图片压缩失败", e);
            }
        });
    }

    /**
     * 异步压缩图片（自定义参数）
     */
    @Async("virtualThreadTaskExecutor")
    public CompletableFuture<ImageCompressUtil.CompressResult> compressImageAsync(
            MultipartFile file, int maxWidth, int maxHeight, float quality, long targetSize) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("开始异步压缩图片（自定义参数）: {}, 目标尺寸: {}x{}, 质量: {}, 目标大小: {}KB", 
                         file.getOriginalFilename(), maxWidth, maxHeight, quality, targetSize / 1024);
                
                ImageCompressUtil.CompressResult result = 
                        ImageCompressUtil.smartCompress(file, maxWidth, maxHeight, quality, targetSize);
                
                // 更新统计信息
                totalProcessed.incrementAndGet();
                totalSaved.addAndGet(result.getOriginalSize() - result.getCompressedSize());
                
                log.debug("图片压缩完成: {}, 压缩率: {:.1f}%", 
                         file.getOriginalFilename(), result.getCompressionRatio());
                
                return result;
                
            } catch (IOException e) {
                totalErrors.incrementAndGet();
                log.error("图片压缩失败: {}, 错误: {}", file.getOriginalFilename(), e.getMessage());
                throw new RuntimeException("图片压缩失败", e);
            }
        });
    }

    /**
     * 批量异步压缩图片
     */
    @Async("virtualThreadTaskExecutor")
    public CompletableFuture<BatchCompressResult> batchCompressAsync(MultipartFile[] files) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            BatchCompressResult batchResult = new BatchCompressResult();
            
            log.info("开始批量压缩 {} 个图片文件", files.length);
            
            for (MultipartFile file : files) {
                try {
                    if (isImageFile(file.getContentType())) {
                        ImageCompressUtil.CompressResult result = ImageCompressUtil.smartCompress(file);
                        batchResult.addSuccess(result);
                        
                        log.debug("批量压缩成功: {}", file.getOriginalFilename());
                    } else {
                        batchResult.addSkipped(file.getOriginalFilename(), "非图片文件");
                        log.debug("跳过非图片文件: {}", file.getOriginalFilename());
                    }
                } catch (Exception e) {
                    batchResult.addError(file.getOriginalFilename(), e.getMessage());
                    log.warn("批量压缩失败: {}, 错误: {}", file.getOriginalFilename(), e.getMessage());
                }
            }
            
            long duration = System.currentTimeMillis() - startTime;
            batchResult.setProcessingTime(duration);
            
            log.info("批量压缩完成: 成功 {}, 跳过 {}, 失败 {}, 耗时 {}ms",
                     batchResult.getSuccessCount(), 
                     batchResult.getSkippedCount(), 
                     batchResult.getErrorCount(),
                     duration);
            
            return batchResult;
        });
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
     * 获取压缩统计信息
     */
    public CompressStats getCompressStats() {
        return new CompressStats(
                totalProcessed.get(),
                totalSaved.get(),
                totalErrors.get()
        );
    }

    /**
     * 重置统计信息
     */
    public void resetStats() {
        totalProcessed.set(0);
        totalSaved.set(0);
        totalErrors.set(0);
        log.info("压缩统计信息已重置");
    }

    /**
     * 批量压缩结果类
     */
    public static class BatchCompressResult {
        private int successCount = 0;
        private int skippedCount = 0;
        private int errorCount = 0;
        private long totalOriginalSize = 0;
        private long totalCompressedSize = 0;
        private long processingTime = 0;

        public void addSuccess(ImageCompressUtil.CompressResult result) {
            successCount++;
            totalOriginalSize += result.getOriginalSize();
            totalCompressedSize += result.getCompressedSize();
        }

        public void addSkipped(String filename, String reason) {
            skippedCount++;
        }

        public void addError(String filename, String error) {
            errorCount++;
        }

        // Getters and setters
        public int getSuccessCount() { return successCount; }
        public int getSkippedCount() { return skippedCount; }
        public int getErrorCount() { return errorCount; }
        public long getTotalOriginalSize() { return totalOriginalSize; }
        public long getTotalCompressedSize() { return totalCompressedSize; }
        public long getProcessingTime() { return processingTime; }
        public void setProcessingTime(long processingTime) { this.processingTime = processingTime; }
        
        public double getTotalCompressionRatio() {
            if (totalOriginalSize == 0) return 0;
            return (1.0 - (double) totalCompressedSize / totalOriginalSize) * 100;
        }
        
        public long getTotalSavedBytes() {
            return totalOriginalSize - totalCompressedSize;
        }
    }

    /**
     * 压缩统计信息类
     */
    public static class CompressStats {
        private final long totalProcessed;
        private final long totalSaved;
        private final long totalErrors;

        public CompressStats(long totalProcessed, long totalSaved, long totalErrors) {
            this.totalProcessed = totalProcessed;
            this.totalSaved = totalSaved;
            this.totalErrors = totalErrors;
        }

        public long getTotalProcessed() { return totalProcessed; }
        public long getTotalSaved() { return totalSaved; }
        public long getTotalErrors() { return totalErrors; }
        public double getErrorRate() {
            return totalProcessed > 0 ? (double) totalErrors / totalProcessed * 100 : 0;
        }
    }
} 