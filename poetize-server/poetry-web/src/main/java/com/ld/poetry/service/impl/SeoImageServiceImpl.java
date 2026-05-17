package com.ld.poetry.service.impl;

import com.ld.poetry.service.SeoImageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.ImageWriteParam;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Subtask;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>
 * SEO图像处理服务实现类
 * </p>
 *
 * @author LeapYa
 * @since 2025-09-25
 */
@Service
@Slf4j
public class SeoImageServiceImpl implements SeoImageService {

    // 支持的图片格式 (预留用于格式验证)
    @SuppressWarnings("unused")
    private static final Set<String> SUPPORTED_FORMATS = Set.of("png", "jpg", "jpeg", "gif", "bmp", "webp");
    
    // 图标类型配置
    private static final Map<String, IconConfig> ICON_CONFIGS = Map.of(
        "favicon", new IconConfig(32, 32, "png"),
        "apple-touch-icon", new IconConfig(180, 180, "png"),
        "icon-192", new IconConfig(192, 192, "png"),
        "icon-512", new IconConfig(512, 512, "png"),
        "poetize-jpg", new IconConfig(512, 512, "jpg"),
        "logo", new IconConfig(256, 256, "png"),
        "banner", new IconConfig(1200, 630, "png")
    );

    @Override
    public Map<String, Object> processImage(MultipartFile imageFile, String targetType, String preferredFormat) {
        try {
            // 验证文件
            if (imageFile == null || imageFile.isEmpty()) {
                return createErrorResult("图片文件为空");
            }

            byte[] originalData = imageFile.getBytes();
            String originalFormat = getImageFormat(originalData);
            
            if (!StringUtils.hasText(originalFormat)) {
                return createErrorResult("不支持的图片格式");
            }

            // 获取目标配置
            IconConfig targetConfig = ICON_CONFIGS.getOrDefault(targetType, 
                new IconConfig(256, 256, "png"));

            String outputFormat = StringUtils.hasText(preferredFormat) ? 
                preferredFormat.toLowerCase() : targetConfig.format;

            // 处理图片
            byte[] processedData = processImageData(originalData, targetConfig, outputFormat);
            
            // 获取处理后的图片信息
            Map<String, Object> imageInfo = analyzeImageData(processedData);
            
            // 计算压缩率
            double compressionRatio = originalData.length > 0 ? 
                (1.0 - (double)processedData.length / originalData.length) * 100 : 0;

            Map<String, Object> result = new HashMap<>();
            result.put("original_size", originalData.length);
            result.put("processed_size", processedData.length);
            result.put("compression_ratio", Math.round(compressionRatio * 100.0) / 100.0);
            result.put("format", outputFormat);
            result.put("info", imageInfo);
            result.put("base64_data", Base64.getEncoder().encodeToString(processedData));


            return createSuccessResult("图片处理成功", result);

        } catch (Exception e) {
            log.error("图片处理失败", e);
            return createErrorResult("图片处理失败: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> batchProcessIcons(MultipartFile imageFile, List<String> iconTypes) {
        try {
            if (imageFile == null || imageFile.isEmpty()) {
                return createErrorResult("图片文件为空");
            }

            if (iconTypes == null || iconTypes.isEmpty()) {
                iconTypes = Arrays.asList("favicon", "apple-touch-icon", "icon-192", "icon-512");
            }

            byte[] originalData = imageFile.getBytes();
            Map<String, Object> results = new ConcurrentHashMap<>();
            
            // 使用并行处理所有图标类型
            try (var scope = StructuredTaskScope.open()) {
                Map<String, Subtask<Map<String, Object>>> iconTasks = new HashMap<>();
                
                // 为每种图标类型创建并行任务
                for (String iconType : iconTypes) {
                    iconTasks.put(iconType, scope.fork(() -> {
                        IconConfig config = ICON_CONFIGS.get(iconType);
                        if (config == null) {
                            return Map.of("error", "未知的图标类型");
                        }

                        byte[] processedData = processImageData(originalData, config, config.format);
                        Map<String, Object> iconInfo = analyzeImageData(processedData);
                        
                        Map<String, Object> iconResult = new HashMap<>();
                        iconResult.put("size", processedData.length);
                        iconResult.put("format", config.format);
                        iconResult.put("dimensions", config.width + "x" + config.height);
                        iconResult.put("info", iconInfo);
                        iconResult.put("base64_data", Base64.getEncoder().encodeToString(processedData));
                        
                        return iconResult;
                    }));
                }
                
                // 等待所有图标处理完成
                scope.join();
                
                // 收集结果
                for (Map.Entry<String, Subtask<Map<String, Object>>> entry : iconTasks.entrySet()) {
                    String iconType = entry.getKey();
                    Subtask<Map<String, Object>> task = entry.getValue();
                    
                    if (task.state() == Subtask.State.SUCCESS) {
                        results.put(iconType, task.get());
                    } else {
                        log.error("处理图标失败 - 类型: {}", iconType);
                        results.put(iconType, Map.of("error", "处理失败"));
                    }
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("图标批量处理被中断", e);
                return createErrorResult("批量处理被中断");
            }

            Map<String, Object> batchResult = new HashMap<>();
            batchResult.put("original_size", originalData.length);
            batchResult.put("processed_count", results.size());
            batchResult.put("icons", results);


            return createSuccessResult("批量处理成功", batchResult);

        } catch (Exception e) {
            log.error("批量图标处理失败", e);
            return createErrorResult("批量处理失败: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> getImageInfo(MultipartFile imageFile) {
        try {
            if (imageFile == null || imageFile.isEmpty()) {
                return createErrorResult("图片文件为空");
            }

            byte[] imageData = imageFile.getBytes();
            Map<String, Object> info = analyzeImageData(imageData);
            
            if (info.containsKey("error")) {
                return createErrorResult("图片分析失败: " + info.get("error"));
            }

            // 添加额外分析信息
            int width = (Integer) info.get("width");
            int height = (Integer) info.get("height");
            
            info.put("file_size_mb", Math.round(imageData.length / (1024.0 * 1024.0) * 100.0) / 100.0);
            info.put("aspect_ratio", height > 0 ? Math.round((double)width / height * 100.0) / 100.0 : 0);

            // 生成使用建议
            List<String> recommendations = generateRecommendations(width, height, (String)info.get("format"));
            info.put("recommendations", recommendations);

            // 兼容性分析
            Map<String, Object> compatibility = analyzeCompatibility(width, height, (String)info.get("format"));
            info.put("compatibility", compatibility);

            return createSuccessResult("图片信息获取成功", info);

        } catch (Exception e) {
            log.error("获取图片信息失败", e);
            return createErrorResult("获取图片信息失败: " + e.getMessage());
        }
    }

    @Override
    public byte[] compressImage(byte[] imageData, float quality, int maxWidth, int maxHeight) {
        try {
            BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(imageData));
            if (originalImage == null) {
                throw new IllegalArgumentException("无效的图片数据");
            }

            // 计算新尺寸
            Dimension newSize = calculateNewSize(originalImage.getWidth(), originalImage.getHeight(), 
                maxWidth, maxHeight, true);

            // 缩放图片
            BufferedImage scaledImage = scaleImage(originalImage, newSize.width, newSize.height);

            // 压缩并输出
            return compressToBytes(scaledImage, "jpg", quality);

        } catch (Exception e) {
            log.error("压缩图片失败", e);
            throw new RuntimeException("压缩图片失败", e);
        }
    }

    @Override
    public byte[] resizeImage(byte[] imageData, int width, int height, boolean keepAspectRatio) {
        try {
            BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(imageData));
            if (originalImage == null) {
                throw new IllegalArgumentException("无效的图片数据");
            }

            Dimension newSize;
            if (keepAspectRatio) {
                newSize = calculateNewSize(originalImage.getWidth(), originalImage.getHeight(), 
                    width, height, true);
            } else {
                newSize = new Dimension(width, height);
            }

            BufferedImage resizedImage = scaleImage(originalImage, newSize.width, newSize.height);
            
            return compressToBytes(resizedImage, "png", 1.0f);

        } catch (Exception e) {
            log.error("调整图片尺寸失败", e);
            throw new RuntimeException("调整图片尺寸失败", e);
        }
    }

    @Override
    public byte[] convertImageFormat(byte[] imageData, String targetFormat) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageData));
            if (image == null) {
                throw new IllegalArgumentException("无效的图片数据");
            }

            return compressToBytes(image, targetFormat.toLowerCase(), 1.0f);

        } catch (Exception e) {
            log.error("转换图片格式失败", e);
            throw new RuntimeException("转换图片格式失败", e);
        }
    }

    // ========== 私有辅助方法 ==========

    private byte[] processImageData(byte[] originalData, IconConfig config, String outputFormat) throws IOException {
        BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(originalData));
        if (originalImage == null) {
            throw new IOException("无法读取图片数据");
        }

        // 调整尺寸
        BufferedImage processedImage = scaleImage(originalImage, config.width, config.height);
        
        // 转换格式并压缩
        float quality = "jpg".equals(outputFormat) || "jpeg".equals(outputFormat) ? 0.9f : 1.0f;
        return compressToBytes(processedImage, outputFormat, quality);
    }

    private BufferedImage scaleImage(BufferedImage originalImage, int targetWidth, int targetHeight) {
        BufferedImage scaledImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = scaledImage.createGraphics();
        
        // 设置高质量渲染
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2d.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
        g2d.dispose();

        return scaledImage;
    }

    private byte[] compressToBytes(BufferedImage image, String format, float quality) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        if ("jpg".equals(format) || "jpeg".equals(format)) {
            // JPEG压缩需要处理透明度
            BufferedImage jpegImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = jpegImage.createGraphics();
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, image.getWidth(), image.getHeight());
            g2d.drawImage(image, 0, 0, null);
            g2d.dispose();

            // 使用质量压缩
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);

            try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                writer.setOutput(ios);
                writer.write(null, new javax.imageio.IIOImage(jpegImage, null, null), param);
            }
            writer.dispose();
        } else {
            // PNG等格式直接输出
            ImageIO.write(image, format, baos);
        }

        return baos.toByteArray();
    }

    private String getImageFormat(byte[] imageData) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageData));
            if (image != null) {
                // 通过文件头判断格式
                if (imageData.length >= 8) {
                    if (imageData[0] == (byte)0x89 && imageData[1] == 0x50 && imageData[2] == 0x4E && imageData[3] == 0x47) {
                        return "png";
                    } else if (imageData[0] == (byte)0xFF && imageData[1] == (byte)0xD8) {
                        return "jpg";
                    } else if (imageData[0] == 0x47 && imageData[1] == 0x49 && imageData[2] == 0x46) {
                        return "gif";
                    }
                }
                return "png"; // 默认
            }
        } catch (Exception e) {
        }
        return null;
    }

    private Map<String, Object> analyzeImageData(byte[] imageData) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageData));
            if (image == null) {
                return Map.of("error", "无法读取图片");
            }

            Map<String, Object> info = new HashMap<>();
            info.put("width", image.getWidth());
            info.put("height", image.getHeight());
            info.put("format", getImageFormat(imageData));
            info.put("color_model", image.getColorModel().toString());
            info.put("has_alpha", image.getColorModel().hasAlpha());
            info.put("pixel_size", image.getWidth() * image.getHeight());

            return info;

        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    private List<String> generateRecommendations(int width, int height, String format) {
        List<String> recommendations = new ArrayList<>();

        // 基于尺寸推荐
        if (width >= 512 && height >= 512) {
            recommendations.add("适合生成PWA图标 (512x512)");
        }
        if (width >= 192 && height >= 192) {
            recommendations.add("适合生成PWA图标 (192x192)");
        }
        if (width >= 180 && height >= 180) {
            recommendations.add("适合生成Apple Touch图标 (180x180)");
        }

        // 基于宽高比推荐
        double aspectRatio = (double) width / height;
        if (Math.abs(aspectRatio - 1.0) < 0.1) {
            recommendations.add("正方形图片，适合做网站图标");
        } else if (Math.abs(aspectRatio - 1.91) < 0.1) {
            recommendations.add("适合做社交媒体分享图片 (1.91:1)");
        }

        // 基于格式推荐
        if ("png".equalsIgnoreCase(format)) {
            recommendations.add("PNG格式支持透明背景，适合做图标");
        } else if ("jpg".equalsIgnoreCase(format) || "jpeg".equalsIgnoreCase(format)) {
            recommendations.add("JPEG格式适合照片，但不支持透明背景");
        }

        return recommendations;
    }

    private Map<String, Object> analyzeCompatibility(int width, int height, String format) {
        Map<String, Object> compatibility = new HashMap<>();
        
        compatibility.put("favicon", width >= 32 && height >= 32);
        compatibility.put("apple_touch_icon", width >= 180 && height >= 180);
        compatibility.put("pwa_icon_192", width >= 192 && height >= 192);
        compatibility.put("pwa_icon_512", width >= 512 && height >= 512);
        compatibility.put("og_image", width >= 1200 && height >= 630);
        compatibility.put("twitter_card", width >= 800 && height >= 400);
        
        return compatibility;
    }

    private Dimension calculateNewSize(int originalWidth, int originalHeight, int maxWidth, int maxHeight, boolean keepAspectRatio) {
        if (!keepAspectRatio) {
            return new Dimension(maxWidth, maxHeight);
        }

        double widthRatio = (double) maxWidth / originalWidth;
        double heightRatio = (double) maxHeight / originalHeight;
        double ratio = Math.min(widthRatio, heightRatio);

        int newWidth = (int) Math.round(originalWidth * ratio);
        int newHeight = (int) Math.round(originalHeight * ratio);

        return new Dimension(newWidth, newHeight);
    }

    private Map<String, Object> createSuccessResult(String message, Object data) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", message);
        result.put("data", data);
        return result;
    }

    private Map<String, Object> createErrorResult(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 500);
        result.put("message", message);
        result.put("data", null);
        return result;
    }

    // 图标配置类
    private static class IconConfig {
        final int width;
        final int height;
        final String format;

        IconConfig(int width, int height, String format) {
            this.width = width;
            this.height = height;
            this.format = format;
        }
    }
}
