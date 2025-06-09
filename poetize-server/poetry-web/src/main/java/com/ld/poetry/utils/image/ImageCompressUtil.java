package com.ld.poetry.utils.image;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

/**
 * 图片压缩工具类
 * 提供高质量的图片压缩功能，支持多种压缩策略
 */
@Slf4j
public class ImageCompressUtil {

    // 压缩配置常量
    private static final int MAX_WIDTH = 1920;        // 最大宽度
    private static final int MAX_HEIGHT = 1080;       // 最大高度
    private static final float DEFAULT_QUALITY = 0.85f; // 默认压缩质量
    private static final long MAX_FILE_SIZE = 500 * 1024; // 目标文件大小：500KB
    
    /**
     * 智能压缩图片
     * 根据图片尺寸和文件大小自动选择最佳压缩策略
     */
    public static CompressResult smartCompress(MultipartFile file) throws IOException {
        return smartCompress(file, MAX_WIDTH, MAX_HEIGHT, DEFAULT_QUALITY, MAX_FILE_SIZE);
    }

    /**
     * 智能压缩图片（自定义参数）
     */
    public static CompressResult smartCompress(MultipartFile file, int maxWidth, int maxHeight, 
                                             float quality, long targetSize) throws IOException {
        // 检查文件类型
        String contentType = file.getContentType();
        if (!isImageFile(contentType)) {
            throw new IllegalArgumentException("不支持的图片格式: " + contentType);
        }

        try (InputStream inputStream = file.getInputStream()) {
            BufferedImage originalImage = ImageIO.read(inputStream);
            if (originalImage == null) {
                throw new IOException("无法读取图片文件");
            }

            long originalSize = file.getSize();
            int originalWidth = originalImage.getWidth();
            int originalHeight = originalImage.getHeight();

            log.info("开始压缩图片 - 原始尺寸: {}x{}, 原始大小: {}KB", 
                    originalWidth, originalHeight, originalSize / 1024);

            // 第一步：尺寸压缩
            BufferedImage resizedImage = resizeImage(originalImage, maxWidth, maxHeight);
            
            // 第二步：质量压缩
            byte[] compressedBytes = compressImageQuality(resizedImage, quality, contentType);
            
            // 第三步：如果还是太大，进一步压缩
            if (compressedBytes.length > targetSize) {
                compressedBytes = progressiveCompress(resizedImage, contentType, targetSize);
            }

            // 第四步：WebP格式转换（可选）
            if (compressedBytes.length > targetSize * 1.2) {
                try {
                    byte[] webpBytes = convertToWebP(resizedImage);
                    if (webpBytes.length < compressedBytes.length) {
                        compressedBytes = webpBytes;
                        contentType = "image/webp";
                    }
                } catch (Exception e) {
                    log.warn("WebP转换失败，使用原格式: {}", e.getMessage());
                }
            }

            long finalSize = compressedBytes.length;
            double compressionRatio = (1.0 - (double) finalSize / originalSize) * 100;

            log.info("图片压缩完成 - 压缩后大小: {}KB, 压缩率: {:.1f}%", 
                    finalSize / 1024, compressionRatio);

            return new CompressResult(compressedBytes, contentType, compressionRatio, originalSize, finalSize);
        }
    }

    /**
     * 尺寸压缩
     */
    private static BufferedImage resizeImage(BufferedImage originalImage, int maxWidth, int maxHeight) {
        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();

        // 如果尺寸已经合适，直接返回
        if (originalWidth <= maxWidth && originalHeight <= maxHeight) {
            return originalImage;
        }

        // 计算缩放比例，保持宽高比
        double scaleWidth = (double) maxWidth / originalWidth;
        double scaleHeight = (double) maxHeight / originalHeight;
        double scale = Math.min(scaleWidth, scaleHeight);

        int newWidth = (int) (originalWidth * scale);
        int newHeight = (int) (originalHeight * scale);

        // 创建高质量的缩放图片
        BufferedImage resizedImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resizedImage.createGraphics();
        
        // 设置高质量渲染
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        
        // 绘制缩放后的图片
        g2d.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
        g2d.dispose();

        return resizedImage;
    }

    /**
     * 质量压缩
     */
    private static byte[] compressImageQuality(BufferedImage image, float quality, String contentType) 
            throws IOException {
        String formatName = getFormatName(contentType);
        
        // JPEG格式支持质量压缩
        if ("jpeg".equalsIgnoreCase(formatName) || "jpg".equalsIgnoreCase(formatName)) {
            return compressJPEG(image, quality);
        }
        
        // PNG格式直接输出
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, formatName, baos);
            return baos.toByteArray();
        }
    }

    /**
     * JPEG质量压缩
     */
    private static byte[] compressJPEG(BufferedImage image, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IOException("没有找到JPEG编码器");
        }

        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            writer.setOutput(ImageIO.createImageOutputStream(baos));
            writer.write(null, new IIOImage(image, null, null), param);
            writer.dispose();
            return baos.toByteArray();
        }
    }

    /**
     * 渐进式压缩
     * 当文件仍然过大时，逐步降低质量直到达到目标大小
     */
    private static byte[] progressiveCompress(BufferedImage image, String contentType, long targetSize) 
            throws IOException {
        float quality = 0.9f;
        byte[] result = null;
        
        while (quality > 0.1f) {
            result = compressImageQuality(image, quality, contentType);
            if (result.length <= targetSize) {
                break;
            }
            quality -= 0.1f;
        }
        
        // 如果质量压缩还是不够，进行尺寸进一步压缩
        if (result != null && result.length > targetSize) {
            int newWidth = (int) (image.getWidth() * 0.8);
            int newHeight = (int) (image.getHeight() * 0.8);
            BufferedImage smallerImage = resizeImage(image, newWidth, newHeight);
            result = compressImageQuality(smallerImage, quality, contentType);
        }
        
        return result;
    }

    /**
     * 转换为WebP格式（如果系统支持）
     */
    private static byte[] convertToWebP(BufferedImage image) throws IOException {
        // 注意：需要添加WebP支持库，这里提供接口
        // 可以使用imageio-webp库或者调用系统的cwebp命令
        throw new UnsupportedOperationException("WebP转换需要额外的库支持");
    }

    /**
     * 检查是否为图片文件
     */
    private static boolean isImageFile(String contentType) {
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
     * 获取图片格式名称
     */
    private static String getFormatName(String contentType) {
        if (contentType.contains("jpeg") || contentType.contains("jpg")) {
            return "jpeg";
        } else if (contentType.contains("png")) {
            return "png";
        } else if (contentType.contains("gif")) {
            return "gif";
        } else if (contentType.contains("bmp")) {
            return "bmp";
        }
        return "jpeg"; // 默认格式
    }

    /**
     * 压缩结果类
     */
    public static class CompressResult {
        private final byte[] data;
        private final String contentType;
        private final double compressionRatio;
        private final long originalSize;
        private final long compressedSize;

        public CompressResult(byte[] data, String contentType, double compressionRatio, 
                            long originalSize, long compressedSize) {
            this.data = data;
            this.contentType = contentType;
            this.compressionRatio = compressionRatio;
            this.originalSize = originalSize;
            this.compressedSize = compressedSize;
        }

        public byte[] getData() { return data; }
        public String getContentType() { return contentType; }
        public double getCompressionRatio() { return compressionRatio; }
        public long getOriginalSize() { return originalSize; }
        public long getCompressedSize() { return compressedSize; }
    }
} 