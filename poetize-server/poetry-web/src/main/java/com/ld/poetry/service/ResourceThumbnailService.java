package com.ld.poetry.service;

import com.ld.poetry.entity.Resource;
import com.ld.poetry.entity.ResourceLocation;
import com.ld.poetry.utils.storage.StoreEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import jakarta.servlet.http.HttpServletResponse;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
@Slf4j
public class ResourceThumbnailService {

    private static final int DEFAULT_WIDTH = 120;
    private static final int DEFAULT_HEIGHT = 104;
    private static final int MIN_SIZE = 40;
    private static final int MAX_SIZE = 400;

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private ResourceAvailabilityService resourceAvailabilityService;

    @Autowired
    private ResourceLocationService resourceLocationService;

    public Thumbnail createThumbnail(Integer id, Integer width, Integer height) throws ThumbnailException {
        int targetWidth = clampSize(width, DEFAULT_WIDTH);
        int targetHeight = clampSize(height, DEFAULT_HEIGHT);

        Resource resource = loadThumbnailResource(id);
        Path sourcePath = resolveSourcePath(resource);

        BufferedImage sourceImage;
        try {
            sourceImage = ImageIO.read(sourcePath.toFile());
        } catch (IOException e) {
            throw new ThumbnailException(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, "图片格式暂不支持缩略图预览");
        }
        if (sourceImage == null || sourceImage.getWidth() <= 0 || sourceImage.getHeight() <= 0) {
            throw new ThumbnailException(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, "图片格式暂不支持缩略图预览");
        }

        boolean hasAlpha = sourceImage.getColorModel().hasAlpha();
        BufferedImage thumbnailImage = resizeCover(sourceImage, targetWidth, targetHeight, hasAlpha);
        String format = hasAlpha ? "png" : "jpg";
        String contentType = hasAlpha ? "image/png" : "image/jpeg";

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            boolean written = ImageIO.write(thumbnailImage, format, outputStream);
            if (!written) {
                throw new ThumbnailException(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, "图片格式暂不支持缩略图预览");
            }
        } catch (IOException e) {
            throw new ThumbnailException(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "生成缩略图失败");
        }

        return new Thumbnail(outputStream.toByteArray(), contentType, buildETag(resource, sourcePath, targetWidth, targetHeight));
    }

    private Resource loadThumbnailResource(Integer id) throws ThumbnailException {
        if (id == null) {
            throw new ThumbnailException(HttpServletResponse.SC_BAD_REQUEST, "资源ID不能为空");
        }

        Resource resource = resourceService.getById(id);
        if (resource == null || !StringUtils.hasText(resource.getPath())) {
            throw new ThumbnailException(HttpServletResponse.SC_NOT_FOUND, "资源不存在或路径为空");
        }
        if (!isLocalStore(resource)) {
            throw new ThumbnailException(HttpServletResponse.SC_BAD_REQUEST, "仅支持服务器本地资源生成缩略图");
        }
        if (!isLocalPath(resource.getPath())) {
            throw new ThumbnailException(HttpServletResponse.SC_BAD_REQUEST, "远程或临时资源不支持生成缩略图");
        }
        if (!isImageResource(resource)) {
            throw new ThumbnailException(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, "当前资源不是图片");
        }
        return resource;
    }

    private Path resolveSourcePath(Resource resource) throws ThumbnailException {
        // 优先用 resource_location.access_path 定位物理文件
        // 归一化后 resource.path 是 /media/{publicId}，无法直接解析为物理路径
        if (resource.getActiveLocationId() != null) {
            try {
                ResourceLocation location = resourceLocationService.requireActiveLocation(resource);
                String accessPath = location.getAccessPath();
                if (StringUtils.hasText(accessPath) && isLocalPath(accessPath)) {
                    List<Path> candidates = resourceAvailabilityService.resolveLocalResourcePaths(accessPath);
                    for (Path candidate : candidates) {
                        if (Files.isRegularFile(candidate)) {
                            return candidate;
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("通过 activeLocation 解析缩略图源文件失败 resourceId={}, err={}",
                        resource.getId(), e.getMessage());
            }
        }

        // 回退到 resource.path（兼容旧资源或未登记物理副本的情况）
        List<Path> candidates = resourceAvailabilityService.resolveLocalResourcePaths(resource.getPath());
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new ThumbnailException(HttpServletResponse.SC_NOT_FOUND, "未找到可生成缩略图的本地文件");
    }

    private boolean isLocalStore(Resource resource) {
        String storeType = resource.getStoreType();
        return !StringUtils.hasText(storeType) || StoreEnum.LOCAL.getCode().equals(storeType);
    }

    private boolean isLocalPath(String path) {
        String lowerPath = path.trim().toLowerCase();
        return !lowerPath.startsWith("http://")
                && !lowerPath.startsWith("https://")
                && !lowerPath.startsWith("//")
                && !lowerPath.startsWith("data:")
                && !lowerPath.startsWith("blob:");
    }

    private boolean isImageResource(Resource resource) {
        String mimeType = resource.getMimeType();
        if (StringUtils.hasText(mimeType) && mimeType.toLowerCase().contains("image")) {
            return true;
        }
        // 归一化后 path 是 /media/{publicId} 无扩展名，用 originalName 兜底
        String path = resource.getPath();
        if (StringUtils.hasText(path)
                && path.toLowerCase().matches(".*\\.(png|jpe?g|gif|svg|webp|bmp|avif|ico)(?:[?#].*)?$")) {
            return true;
        }
        String originalName = resource.getOriginalName();
        return StringUtils.hasText(originalName)
                && originalName.toLowerCase().matches(".*\\.(png|jpe?g|gif|svg|webp|bmp|avif|ico)(?:[?#].*)?$");
    }

    private int clampSize(Integer requestedSize, int defaultSize) {
        if (requestedSize == null) {
            return defaultSize;
        }
        return Math.min(MAX_SIZE, Math.max(MIN_SIZE, requestedSize));
    }

    private BufferedImage resizeCover(BufferedImage source, int targetWidth, int targetHeight, boolean hasAlpha) {
        int imageType = hasAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage thumbnail = new BufferedImage(targetWidth, targetHeight, imageType);
        Graphics2D graphics = thumbnail.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (!hasAlpha) {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, targetWidth, targetHeight);
            }

            double scale = Math.max((double) targetWidth / source.getWidth(), (double) targetHeight / source.getHeight());
            int scaledWidth = (int) Math.ceil(source.getWidth() * scale);
            int scaledHeight = (int) Math.ceil(source.getHeight() * scale);
            int x = (targetWidth - scaledWidth) / 2;
            int y = (targetHeight - scaledHeight) / 2;
            graphics.drawImage(source, x, y, scaledWidth, scaledHeight, null);
            return thumbnail;
        } finally {
            graphics.dispose();
        }
    }

    private String buildETag(Resource resource, Path sourcePath, int width, int height) {
        String version = StringUtils.hasText(resource.getResourceHash())
                ? resource.getResourceHash()
                : String.valueOf(resource.getSize());
        long lastModified = 0L;
        try {
            lastModified = Files.getLastModifiedTime(sourcePath).toMillis();
        } catch (IOException ignored) {
        }
        return "\"resource-thumbnail-" + resource.getId() + "-" + width + "x" + height + "-" + version + "-" + lastModified + "\"";
    }

    public static class Thumbnail {
        private final byte[] bytes;
        private final String contentType;
        private final String eTag;

        private Thumbnail(byte[] bytes, String contentType, String eTag) {
            this.bytes = bytes;
            this.contentType = contentType;
            this.eTag = eTag;
        }

        public byte[] getBytes() {
            return bytes;
        }

        public String getContentType() {
            return contentType;
        }

        public String getETag() {
            return eTag;
        }
    }

    public static class ThumbnailException extends Exception {
        private final int statusCode;

        private ThumbnailException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }
}
