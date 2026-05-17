package com.ld.poetry.service;

import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.entity.Resource;
import com.ld.poetry.utils.font.Woff2Encoder;
import com.ld.poetry.utils.security.FileSecurityValidator;
import com.ld.poetry.utils.storage.StoreEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class ResourceReplaceService {

    private static final Set<String> STATIC_IMAGE_TARGET_EXTENSIONS = Set.of("png", "jpg");
    private static final Set<String> FONT_EXTENSIONS = Set.of("woff2", "woff", "ttf", "otf", "eot");
    private static final Set<String> ANIMATED_IMAGE_EXTENSIONS = Set.of("gif", "webp", "apng");
    private static final Set<String> VIDEO_EXTENSIONS = Set.of("mp4", "webm", "mov", "m4v", "ogg", "ogv");
    private static final Set<String> MEDIA_CONVERTER_TARGET_EXTENSIONS = Set.of("mp4", "webm", "gif", "webp", "apng");
    private static final Set<String> MEDIA_CONVERTER_INPUT_EXTENSIONS = Set.of("mp4", "webm", "mov", "m4v", "ogg", "ogv", "gif", "webp", "apng");

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private FileSecurityValidator fileSecurityValidator;

    @Value("${local.uploadUrl:/app/static/}")
    private String localUploadUrl;

    @Value("${local.downloadUrl:/static/}")
    private String localDownloadUrl;

    @Value("${resource.availability.staticRoots:}")
    private String staticResourceRoots;

    @Value("${resource.replace.autoStaticRootDiscovery:true}")
    private boolean autoStaticRootDiscovery;

    @Value("${resource.replace.mediaConverter.path:${RESOURCE_REPLACE_MEDIA_CONVERTER_PATH:ffmpeg}}")
    private String mediaConverterPath = "ffmpeg";

    @Value("${resource.replace.mediaConverter.timeout:60}")
    private long mediaConverterTimeoutSeconds = 60;

    @Value("${resource.replace.mediaConverter.maxDurationSeconds:30}")
    private long mediaConverterMaxDurationSeconds = 30;

    @Transactional(rollbackFor = Exception.class)
    public PoetryResult<Resource> replaceResource(Integer id, String expectedPath, MultipartFile file) {
        List<ReplaceRecord> replacedRecords = Collections.emptyList();
        try {
            if (id == null) {
                return PoetryResult.fail("资源ID不能为空！");
            }
            if (!StringUtils.hasText(expectedPath)) {
                return PoetryResult.fail("资源路径不能为空！");
            }
            if (file == null || file.isEmpty()) {
                return PoetryResult.fail("请选择要替换的文件！");
            }
            if (file.getSize() > Integer.MAX_VALUE) {
                return PoetryResult.fail("文件大小超过系统限制，请上传较小的文件！");
            }

            Resource resource = resourceService.getById(id);
            if (resource == null || !StringUtils.hasText(resource.getPath())) {
                return PoetryResult.fail("资源不存在或路径为空！");
            }
            if (!resource.getPath().equals(expectedPath)) {
                return PoetryResult.fail("资源路径已变化，请刷新列表后再替换！");
            }
            if (!isLocalReplaceableResource(resource)) {
                return PoetryResult.fail("当前存储平台不支持原路径替换，仅支持服务器本地资源！");
            }
            if (!isPathReplaceable(resource.getPath())) {
                return PoetryResult.fail("该资源路径不支持原路径替换！");
            }

            List<Path> targetPaths = resolveReplacementTargets(resource.getPath());
            if (targetPaths.isEmpty()) {
                return PoetryResult.fail("未找到可替换的本地文件，请确认资源文件存在且 static root 配置正确！");
            }
            for (Path targetPath : targetPaths) {
                if (!Files.isWritable(targetPath) || !Files.isWritable(targetPath.getParent())) {
                    return PoetryResult.fail("目标文件不可写：" + targetPath);
                }
            }

            ReplacementPayload replacementPayload = prepareReplacementPayload(resource, file);
            if (!replacementPayload.success) {
                return PoetryResult.fail(replacementPayload.message);
            }

            byte[] replacementBytes = replacementPayload.bytes;
            if (replacementBytes.length == 0) {
                return PoetryResult.fail("替换文件不能为空！");
            }

            Resource updatedResource = buildUpdatedResource(resource, replacementPayload, targetPaths.get(0));
            replacedRecords = replaceFiles(targetPaths, replacementBytes);
            boolean updated = resourceService.updateById(updatedResource);
            if (!updated) {
                throw new IOException("资源元数据更新失败");
            }

            deleteBackups(replacedRecords);
            applyUpdatedFields(resource, updatedResource);
            return PoetryResult.success(resource);
        } catch (Exception e) {
            rollbackFiles(replacedRecords);
            log.warn("替换资源失败: id={}, path={}, message={}", id, expectedPath, e.getMessage(), e);
            return PoetryResult.fail("替换资源失败: " + e.getMessage());
        }
    }

    private boolean isLocalReplaceableResource(Resource resource) {
        String storeType = resource.getStoreType();
        return !StringUtils.hasText(storeType) || StoreEnum.LOCAL.getCode().equals(storeType);
    }

    private boolean isPathReplaceable(String resourcePath) {
        if (!StringUtils.hasText(resourcePath)) {
            return false;
        }
        String lowerPath = resourcePath.trim().toLowerCase();
        return !lowerPath.startsWith("http://")
                && !lowerPath.startsWith("https://")
                && !lowerPath.startsWith("//")
                && !lowerPath.startsWith("data:")
                && !lowerPath.startsWith("blob:");
    }

    private ReplacementPayload prepareReplacementPayload(Resource resource,
                                                         MultipartFile file) throws IOException {
        String targetExtension = normalizeExtension(extensionOf(stripQueryAndFragment(resource.getPath())));
        if (!StringUtils.hasText(targetExtension)) {
            return ReplacementPayload.fail("资源路径缺少扩展名，无法安全替换！");
        }

        if (isConvertibleImageTarget(targetExtension)) {
            return prepareImageReplacementPayload(resource, file, targetExtension);
        }

        if ("woff2".equals(targetExtension)) {
            return prepareWoff2ReplacementPayload(resource, file);
        }

        if (isMediaResourceExtension(targetExtension)) {
            return prepareMediaReplacementPayload(resource, file, targetExtension);
        }

        if (FONT_EXTENSIONS.contains(targetExtension)) {
            return prepareSameExtensionPayload(resource, file);
        }

        return prepareSameExtensionPayload(resource, file);
    }

    private ReplacementPayload prepareSameExtensionPayload(Resource resource,
                                                          MultipartFile file) throws IOException {
        if (!isExtensionCompatible(resource.getPath(), file.getOriginalFilename())) {
            return ReplacementPayload.fail("替换文件扩展名必须与原资源一致（jpg 与 jpeg 视为一致）！");
        }
        FileSecurityValidator.ValidationResult validationResult = validateUploadedFile(file);
        if (!validationResult.isSuccess()) {
            return ReplacementPayload.fail("文件验证失败: " + validationResult.getMessage());
        }
        return ReplacementPayload.success(file.getBytes(), file.getOriginalFilename(), file.getContentType());
    }

    private ReplacementPayload prepareWoff2ReplacementPayload(Resource resource,
                                                              MultipartFile file) throws IOException {
        String uploadedExtension = normalizeExtension(extensionOf(file.getOriginalFilename()));
        if (!Set.of("woff2", "ttf", "otf").contains(uploadedExtension)) {
            return ReplacementPayload.fail("WOFF2 资源只能使用 WOFF2 原样替换，或上传 TTF/OTF 自动转换为 WOFF2！");
        }

        FileSecurityValidator.ValidationResult validationResult = validateUploadedFile(file);
        if (!validationResult.isSuccess()) {
            return ReplacementPayload.fail("文件验证失败: " + validationResult.getMessage());
        }

        if ("woff2".equals(uploadedExtension)) {
            return ReplacementPayload.success(file.getBytes(), file.getOriginalFilename(), "font/woff2");
        }

        byte[] encodedBytes = encodeWoff2Bytes(file.getBytes());
        return ReplacementPayload.success(encodedBytes, targetFileName(resource.getPath()), "font/woff2");
    }

    private ReplacementPayload prepareMediaReplacementPayload(Resource resource,
                                                             MultipartFile file,
                                                             String targetExtension) throws IOException {
        String uploadedExtension = normalizeExtension(extensionOf(file.getOriginalFilename()));
        if (!StringUtils.hasText(uploadedExtension)) {
            return ReplacementPayload.fail("替换文件缺少扩展名，无法判断是否支持转换！");
        }

        if (targetExtension.equals(uploadedExtension)) {
            FileSecurityValidator.ValidationResult validationResult = validateUploadedFile(file);
            if (!validationResult.isSuccess()) {
                return ReplacementPayload.fail("文件验证失败: " + validationResult.getMessage());
            }
            return ReplacementPayload.success(
                    file.getBytes(),
                    file.getOriginalFilename(),
                    resolveMimeTypeForExtension(targetExtension, file.getContentType())
            );
        }

        if (!MEDIA_CONVERTER_TARGET_EXTENSIONS.contains(targetExtension)
                || !MEDIA_CONVERTER_INPUT_EXTENSIONS.contains(uploadedExtension)) {
            return ReplacementPayload.fail("替换文件扩展名必须与原资源一致；动图/视频互转仅支持 GIF/WebP/APNG 与 MP4/WebM 等常见格式！");
        }
        FileSecurityValidator.ValidationResult validationResult = validateUploadedFile(file);
        if (!validationResult.isSuccess()) {
            return ReplacementPayload.fail("文件验证失败: " + validationResult.getMessage());
        }

        byte[] convertedBytes = convertMediaBytes(file, targetExtension);
        return ReplacementPayload.success(
                convertedBytes,
                targetFileName(resource.getPath()),
                resolveMimeTypeForExtension(targetExtension, "")
        );
    }

    private ReplacementPayload prepareImageReplacementPayload(Resource resource,
                                                             MultipartFile file,
                                                             String targetExtension) throws IOException {
        String uploadedExtension = normalizeExtension(extensionOf(file.getOriginalFilename()));
        if (!isUploadedImage(file, uploadedExtension)) {
            return ReplacementPayload.fail("图片资源只能使用图片文件替换；请先在浏览器中截取视频帧或手动上传图片！");
        }

        FileSecurityValidator.ValidationResult validationResult = validateUploadedFile(file);
        if (!validationResult.isSuccess()) {
            return ReplacementPayload.fail("文件验证失败: " + validationResult.getMessage());
        }

        BufferedImage uploadedImage = readUploadedImage(file, uploadedExtension);
        if (uploadedImage == null) {
            return ReplacementPayload.fail("上传文件不是可转换的图片，无法替换当前图片资源！");
        }

        if (isExtensionCompatible(resource.getPath(), file.getOriginalFilename())) {
            return ReplacementPayload.success(file.getBytes(), file.getOriginalFilename(), resolveImageMimeType(targetExtension));
        }
        return encodeConvertedImage(uploadedImage, targetExtension, targetFileName(resource.getPath()));
    }

    private FileSecurityValidator.ValidationResult validateUploadedFile(MultipartFile file) {
        return fileSecurityValidator.validateFile(file, file.getOriginalFilename(), file.getContentType());
    }

    protected byte[] encodeWoff2Bytes(byte[] fontBytes) throws IOException {
        return Woff2Encoder.encode(fontBytes);
    }

    protected byte[] convertMediaBytes(MultipartFile file, String targetExtension) throws IOException {
        if (!StringUtils.hasText(mediaConverterPath)) {
            throw new IOException("媒体转换器路径未配置");
        }
        Path inputPath = null;
        Path outputPath = null;
        Path logPath = null;
        try {
            String uploadedExtension = normalizeExtension(extensionOf(file.getOriginalFilename()));
            String inputSuffix = StringUtils.hasText(uploadedExtension) ? "." + uploadedExtension : ".bin";
            inputPath = Files.createTempFile("resource_replace_media_input_", inputSuffix);
            outputPath = Files.createTempFile("resource_replace_media_output_", "." + targetExtension);
            logPath = Files.createTempFile("resource_replace_media_", ".log");
            Files.write(inputPath, file.getBytes());

            List<String> command = buildMediaConverterCommand(inputPath, outputPath, targetExtension);
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            processBuilder.redirectOutput(logPath.toFile());
            Process process = processBuilder.start();
            boolean finished = process.waitFor(resolvePositiveLong(mediaConverterTimeoutSeconds, 60), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("媒体转换器执行超时");
            }
            if (process.exitValue() != 0 || !Files.exists(outputPath) || Files.size(outputPath) == 0) {
                String message = readLogSnippet(logPath);
                throw new IOException("媒体转换失败" + (StringUtils.hasText(message) ? "：" + message : ""));
            }
            return Files.readAllBytes(outputPath);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("媒体转换被中断", e);
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("Cannot run program")) {
                throw new IOException("媒体转换器不可用，请检查 resource.replace.mediaConverter.path 配置", e);
            }
            throw e;
        } finally {
            safeDelete(inputPath);
            safeDelete(outputPath);
            safeDelete(logPath);
        }
    }

    private List<String> buildMediaConverterCommand(Path inputPath, Path outputPath, String targetExtension) {
        long maxDuration = resolvePositiveLong(mediaConverterMaxDurationSeconds, 30);
        List<String> command = new ArrayList<>();
        command.add(mediaConverterPath.trim());
        command.add("-hide_banner");
        command.add("-y");
        command.add("-i");
        command.add(inputPath.toAbsolutePath().toString());
        command.add("-t");
        command.add(String.valueOf(maxDuration));

        if ("mp4".equals(targetExtension)) {
            command.add("-vf");
            command.add("scale=trunc(iw/2)*2:trunc(ih/2)*2");
            command.add("-c:v");
            command.add("libx264");
            command.add("-pix_fmt");
            command.add("yuv420p");
            command.add("-movflags");
            command.add("+faststart");
            command.add("-an");
        } else if ("webm".equals(targetExtension)) {
            command.add("-c:v");
            command.add("libvpx-vp9");
            command.add("-b:v");
            command.add("0");
            command.add("-crf");
            command.add("35");
            command.add("-an");
        } else if ("gif".equals(targetExtension)) {
            command.add("-vf");
            command.add("fps=12,scale=480:-1:flags=lanczos");
            command.add("-loop");
            command.add("0");
        } else if ("webp".equals(targetExtension)) {
            command.add("-vf");
            command.add("fps=12,scale=480:-1:flags=lanczos");
            command.add("-loop");
            command.add("0");
            command.add("-lossless");
            command.add("0");
            command.add("-quality");
            command.add("80");
            command.add("-an");
        } else if ("apng".equals(targetExtension)) {
            command.add("-f");
            command.add("apng");
            command.add("-plays");
            command.add("0");
        }

        command.add(outputPath.toAbsolutePath().toString());
        return command;
    }

    private long resolvePositiveLong(long value, long defaultValue) {
        return value > 0 ? value : defaultValue;
    }

    private String readLogSnippet(Path logPath) {
        if (logPath == null || !Files.exists(logPath)) {
            return "";
        }
        try {
            String log = Files.readString(logPath).trim();
            if (log.length() > 500) {
                return log.substring(log.length() - 500);
            }
            return log;
        } catch (IOException e) {
            return "";
        }
    }

    private boolean isConvertibleImageTarget(String extension) {
        return STATIC_IMAGE_TARGET_EXTENSIONS.contains(extension);
    }

    private boolean isMediaResourceExtension(String extension) {
        return ANIMATED_IMAGE_EXTENSIONS.contains(extension) || VIDEO_EXTENSIONS.contains(extension);
    }

    private boolean isUploadedImage(MultipartFile file, String extension) {
        String contentType = String.valueOf(file.getContentType() == null ? "" : file.getContentType())
                .toLowerCase(Locale.ROOT);
        return contentType.startsWith("image/") ||
                Set.of("jpg", "jpeg", "png", "webp", "bmp", "gif", "apng", "tif", "tiff", "svg").contains(extension);
    }

    private boolean isExtensionCompatible(String targetPath, String uploadedName) {
        String targetExtension = normalizeExtension(extensionOf(stripQueryAndFragment(targetPath)));
        String uploadedExtension = normalizeExtension(extensionOf(uploadedName));
        return StringUtils.hasText(targetExtension)
                && StringUtils.hasText(uploadedExtension)
                && targetExtension.equals(uploadedExtension);
    }

    private String extensionOf(String path) {
        if (!StringUtils.hasText(path)) {
            return "";
        }
        String normalizedPath = path.replace('\\', '/');
        int slashIndex = normalizedPath.lastIndexOf('/');
        int dotIndex = normalizedPath.lastIndexOf('.');
        if (dotIndex <= slashIndex || dotIndex == normalizedPath.length() - 1) {
            return "";
        }
        return normalizedPath.substring(dotIndex + 1).toLowerCase();
    }

    private String normalizeExtension(String extension) {
        if ("jpeg".equals(extension)) {
            return "jpg";
        }
        return extension;
    }

    private ReplacementPayload encodeConvertedImage(BufferedImage image,
                                                    String targetExtension,
                                                    String targetFileName) throws IOException {
        if ("png".equals(targetExtension)) {
            return ReplacementPayload.success(writePng(image), targetFileName, "image/png");
        }
        if ("jpg".equals(targetExtension)) {
            return ReplacementPayload.success(writeJpeg(image), targetFileName, "image/jpeg");
        }
        return ReplacementPayload.fail("当前图片格式暂不支持自动转换！");
    }

    private byte[] writePng(BufferedImage image) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            boolean written = ImageIO.write(toArgbImage(image), "png", outputStream);
            if (!written) {
                throw new IOException("PNG 编码失败");
            }
            return outputStream.toByteArray();
        }
    }

    private byte[] writeJpeg(BufferedImage image) throws IOException {
        BufferedImage rgbImage = flattenToRgb(image);
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IOException("JPEG 编码器不可用");
        }
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ImageOutputStream imageOutputStream = ImageIO.createImageOutputStream(outputStream)) {
            writer.setOutput(imageOutputStream);
            ImageWriteParam writeParam = writer.getDefaultWriteParam();
            if (writeParam.canWriteCompressed()) {
                writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                writeParam.setCompressionQuality(0.92f);
            }
            writer.write(null, new IIOImage(rgbImage, null, null), writeParam);
            imageOutputStream.flush();
            return outputStream.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    private BufferedImage toArgbImage(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_ARGB) {
            return source;
        }
        BufferedImage image = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            applyQualityRenderingHints(graphics);
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private BufferedImage flattenToRgb(BufferedImage source) {
        BufferedImage image = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            applyQualityRenderingHints(graphics);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private void applyQualityRenderingHints(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }

    private BufferedImage readUploadedImage(MultipartFile file, String extension) throws IOException {
        try (InputStream inputStream = file.getInputStream()) {
            BufferedImage image = ImageIO.read(inputStream);
            if (image != null) {
                return image;
            }
        }
        if ("webp".equals(extension)) {
            return readWebPViaDwebp(file.getBytes());
        }
        return null;
    }

    private BufferedImage readImage(byte[] bytes) {
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            return null;
        }
    }

    private BufferedImage readWebPViaDwebp(byte[] webpBytes) throws IOException {
        Path webpPath = null;
        Path pngPath = null;
        Path logPath = null;
        try {
            webpPath = Files.createTempFile("resource_replace_webp_", ".webp");
            pngPath = Files.createTempFile("resource_replace_webp_", ".png");
            logPath = Files.createTempFile("resource_replace_webp_", ".log");
            Files.write(webpPath, webpBytes);
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "dwebp",
                    webpPath.toAbsolutePath().toString(),
                    "-o",
                    pngPath.toAbsolutePath().toString()
            );
            processBuilder.redirectErrorStream(true);
            processBuilder.redirectOutput(logPath.toFile());
            Process process = processBuilder.start();
            boolean finished = process.waitFor(15, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("dwebp 执行超时，无法解码 WebP 图片");
            }
            if (process.exitValue() != 0 || !Files.exists(pngPath) || Files.size(pngPath) == 0) {
                throw new IOException("dwebp 解码 WebP 图片失败");
            }
            return ImageIO.read(pngPath.toFile());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("dwebp 解码 WebP 图片被中断", e);
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("Cannot run program")) {
                throw new IOException("dwebp 不可用，无法解码 WebP 图片", e);
            }
            throw e;
        } finally {
            safeDelete(webpPath);
            safeDelete(pngPath);
            safeDelete(logPath);
        }
    }

    private String resolveImageMimeType(String targetExtension) {
        return "png".equals(targetExtension) ? "image/png" : "image/jpeg";
    }

    private String resolveMimeTypeForExtension(String extension, String fallbackMimeType) {
        if ("jpg".equals(extension)) {
            return "image/jpeg";
        }
        if ("png".equals(extension)) {
            return "image/png";
        }
        if ("gif".equals(extension)) {
            return "image/gif";
        }
        if ("webp".equals(extension)) {
            return "image/webp";
        }
        if ("apng".equals(extension)) {
            return "image/apng";
        }
        if ("mp4".equals(extension)) {
            return "video/mp4";
        }
        if ("webm".equals(extension)) {
            return "video/webm";
        }
        if ("ogg".equals(extension) || "ogv".equals(extension)) {
            return "video/ogg";
        }
        if ("mov".equals(extension)) {
            return "video/quicktime";
        }
        if ("m4v".equals(extension)) {
            return "video/x-m4v";
        }
        if ("woff2".equals(extension)) {
            return "font/woff2";
        }
        if (StringUtils.hasText(fallbackMimeType)) {
            return fallbackMimeType;
        }
        return "application/octet-stream";
    }

    private String targetFileName(String resourcePath) {
        String cleanPath = stripQueryAndFragment((resourcePath == null ? "" : resourcePath).replace('\\', '/'));
        int slashIndex = cleanPath.lastIndexOf('/');
        String fileName = slashIndex >= 0 ? cleanPath.substring(slashIndex + 1) : cleanPath;
        return StringUtils.hasText(fileName) ? fileName : "resource";
    }

    private Resource buildUpdatedResource(Resource currentResource,
                                          ReplacementPayload replacementPayload,
                                          Path targetPath) throws IOException {
        Resource updated = new Resource();
        updated.setId(currentResource.getId());
        updated.setSize(Integer.valueOf(replacementPayload.bytes.length));
        updated.setOriginalName(replacementPayload.originalName);
        updated.setMimeType(resolveMimeType(replacementPayload, targetPath));
        updated.setResourceHash(DigestUtils.sha256Hex(replacementPayload.bytes));

        int[] dimensions = readImageDimensions(replacementPayload.bytes);
        if (dimensions != null) {
            updated.setWidth(dimensions[0]);
            updated.setHeight(dimensions[1]);
        }
        return updated;
    }

    private String resolveMimeType(ReplacementPayload replacementPayload, Path targetPath) throws IOException {
        if (StringUtils.hasText(replacementPayload.mimeType)) {
            return replacementPayload.mimeType;
        }
        String probedType = Files.probeContentType(targetPath);
        return StringUtils.hasText(probedType) ? probedType : "application/octet-stream";
    }

    private int[] readImageDimensions(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                return null;
            }
            return new int[]{image.getWidth(), image.getHeight()};
        } catch (IOException e) {
            return null;
        }
    }

    private void applyUpdatedFields(Resource resource, Resource updatedResource) {
        resource.setSize(updatedResource.getSize());
        resource.setOriginalName(updatedResource.getOriginalName());
        resource.setMimeType(updatedResource.getMimeType());
        resource.setResourceHash(updatedResource.getResourceHash());
        resource.setWidth(updatedResource.getWidth());
        resource.setHeight(updatedResource.getHeight());
    }

    private List<Path> resolveReplacementTargets(String resourcePath) {
        String cleanPath = stripQueryAndFragment(resourcePath.replace('\\', '/'));
        String originalRelativePath = trimLeadingSlashes(cleanPath);
        String strippedRelativePath = stripDownloadPrefix(cleanPath);
        if (!StringUtils.hasText(strippedRelativePath)) {
            return Collections.emptyList();
        }

        Set<Path> paths = new LinkedHashSet<>();
        Path uploadRoot = Paths.get(normalizeLocalUploadUrl()).toAbsolutePath().normalize();
        addExistingResolvedPath(paths, uploadRoot, strippedRelativePath);

        for (StaticRoot staticRoot : buildStaticResourceRoots()) {
            if (staticRoot.mode == StaticRootMode.STRIPPED || staticRoot.mode == StaticRootMode.BOTH) {
                addExistingResolvedPath(paths, staticRoot.root, strippedRelativePath);
            }
            if (staticRoot.mode == StaticRootMode.FULL || staticRoot.mode == StaticRootMode.BOTH) {
                addExistingResolvedPath(paths, staticRoot.root, originalRelativePath);
            }
        }
        return new ArrayList<>(paths);
    }

    private void addExistingResolvedPath(Set<Path> paths, Path basePath, String relativePath) {
        if (basePath == null || !StringUtils.hasText(relativePath)) {
            return;
        }
        Path normalizedBase = basePath.toAbsolutePath().normalize();
        Path resolvedPath = normalizedBase.resolve(relativePath.replace("/", File.separator)).normalize();
        if (resolvedPath.startsWith(normalizedBase) && Files.isRegularFile(resolvedPath)) {
            paths.add(resolvedPath);
        }
    }

    private List<StaticRoot> buildStaticResourceRoots() {
        List<StaticRoot> roots = new ArrayList<>();
        addConfiguredStaticRoots(roots);
        if (!autoStaticRootDiscovery) {
            return roots;
        }

        Path currentPath = Paths.get("").toAbsolutePath().normalize();
        List<Path> baseCandidates = new ArrayList<>();
        for (Path path = currentPath; path != null; path = path.getParent()) {
            baseCandidates.add(path);
        }

        for (Path baseCandidate : baseCandidates) {
            addStaticRootVariants(roots, baseCandidate);
        }
        return roots;
    }

    private void addConfiguredStaticRoots(List<StaticRoot> roots) {
        if (!StringUtils.hasText(staticResourceRoots)) {
            return;
        }
        for (String root : staticResourceRoots.split(",")) {
            if (StringUtils.hasText(root)) {
                addRoot(roots, Paths.get(root.trim()), StaticRootMode.BOTH);
            }
        }
    }

    private void addStaticRootVariants(List<StaticRoot> roots, Path basePath) {
        addRoot(roots, basePath.resolve("public/static"), StaticRootMode.STRIPPED);
        addRoot(roots, basePath.resolve("public"), StaticRootMode.FULL);
        addRoot(roots, basePath.resolve("dist/static"), StaticRootMode.STRIPPED);
        addRoot(roots, basePath.resolve("dist"), StaticRootMode.FULL);
        addRoot(roots, basePath.resolve("web-dist/static"), StaticRootMode.STRIPPED);
        addRoot(roots, basePath.resolve("poetize-web/public/static"), StaticRootMode.STRIPPED);
        addRoot(roots, basePath.resolve("poetize-web/public"), StaticRootMode.FULL);
        addRoot(roots, basePath.resolve("poetize-web/dist/static"), StaticRootMode.STRIPPED);
        addRoot(roots, basePath.resolve("poetize-web/dist"), StaticRootMode.FULL);
        addRoot(roots, basePath.resolve("poetize-admin/public/static"), StaticRootMode.STRIPPED);
        addRoot(roots, basePath.resolve("poetize-admin/public"), StaticRootMode.FULL);
        addRoot(roots, basePath.resolve("poetize-admin/dist/static"), StaticRootMode.STRIPPED);
        addRoot(roots, basePath.resolve("poetize-admin/dist"), StaticRootMode.FULL);
    }

    private void addRoot(List<StaticRoot> roots, Path root, StaticRootMode mode) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalizedRoot)) {
            return;
        }
        StaticRoot staticRoot = new StaticRoot(normalizedRoot, mode);
        if (!roots.contains(staticRoot)) {
            roots.add(staticRoot);
        }
    }

    private String stripDownloadPrefix(String resourcePath) {
        String relativePath = resourcePath;
        String normalizedDownloadUrl = normalizeDownloadUrl();
        if (StringUtils.hasText(normalizedDownloadUrl) && relativePath.startsWith(normalizedDownloadUrl)) {
            relativePath = relativePath.substring(normalizedDownloadUrl.length());
        } else if (relativePath.startsWith("/static/")) {
            relativePath = relativePath.substring("/static/".length());
        }
        return trimLeadingSlashes(relativePath);
    }

    private String normalizeLocalUploadUrl() {
        String uploadPath = StringUtils.hasText(localUploadUrl) ? localUploadUrl : "/app/static/";
        if (uploadPath.startsWith("file:")) {
            uploadPath = uploadPath.substring("file:".length());
        }
        return uploadPath;
    }

    private String normalizeDownloadUrl() {
        if (!StringUtils.hasText(localDownloadUrl)) {
            return "";
        }
        return localDownloadUrl.replace('\\', '/');
    }

    private String stripQueryAndFragment(String path) {
        if (!StringUtils.hasText(path)) {
            return "";
        }
        int queryIndex = path.indexOf('?');
        int fragmentIndex = path.indexOf('#');
        int cutIndex = -1;
        if (queryIndex >= 0) {
            cutIndex = queryIndex;
        }
        if (fragmentIndex >= 0 && (cutIndex < 0 || fragmentIndex < cutIndex)) {
            cutIndex = fragmentIndex;
        }
        return cutIndex >= 0 ? path.substring(0, cutIndex) : path;
    }

    private String trimLeadingSlashes(String path) {
        String result = path;
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        return result;
    }

    private List<ReplaceRecord> replaceFiles(List<Path> targetPaths, byte[] replacementBytes) throws IOException {
        List<ReplaceRecord> records = new ArrayList<>();
        for (Path targetPath : targetPaths) {
            ReplaceRecord record = replaceOneFile(targetPath, replacementBytes);
            records.add(record);
        }
        return records;
    }

    private ReplaceRecord replaceOneFile(Path targetPath, byte[] replacementBytes) throws IOException {
        Path parent = targetPath.getParent();
        Path tempPath = Files.createTempFile(parent, targetPath.getFileName() + ".", ".replacing");
        Path backupPath = Files.createTempFile(parent, targetPath.getFileName() + ".", ".backup");
        boolean backupCreated = false;
        try {
            Files.write(tempPath, replacementBytes);
            moveReplace(targetPath, backupPath);
            backupCreated = true;
            moveReplace(tempPath, targetPath);
            return new ReplaceRecord(targetPath, backupPath);
        } catch (IOException e) {
            safeDelete(tempPath);
            if (backupCreated && Files.exists(backupPath)) {
                moveReplace(backupPath, targetPath);
            } else {
                safeDelete(backupPath);
            }
            throw e;
        }
    }

    private void rollbackFiles(List<ReplaceRecord> records) {
        for (int i = records.size() - 1; i >= 0; i--) {
            ReplaceRecord record = records.get(i);
            try {
                if (Files.exists(record.backupPath)) {
                    moveReplace(record.backupPath, record.targetPath);
                }
            } catch (IOException e) {
                log.error("回滚替换文件失败: {}", record.targetPath, e);
            }
        }
    }

    private void deleteBackups(List<ReplaceRecord> records) {
        for (ReplaceRecord record : records) {
            safeDelete(record.backupPath);
        }
    }

    private void safeDelete(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.debug("删除临时文件失败: {}", path, e);
        }
    }

    private void moveReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private enum StaticRootMode {
        STRIPPED,
        FULL,
        BOTH
    }

    private static class StaticRoot {
        private final Path root;
        private final StaticRootMode mode;

        private StaticRoot(Path root, StaticRootMode mode) {
            this.root = root;
            this.mode = mode;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof StaticRoot other)) {
                return false;
            }
            return root.equals(other.root) && mode == other.mode;
        }

        @Override
        public int hashCode() {
            return 31 * root.hashCode() + mode.hashCode();
        }
    }

    private static class ReplaceRecord {
        private final Path targetPath;
        private final Path backupPath;

        private ReplaceRecord(Path targetPath, Path backupPath) {
            this.targetPath = targetPath;
            this.backupPath = backupPath;
        }
    }

    private static class ReplacementPayload {
        private final boolean success;
        private final byte[] bytes;
        private final String originalName;
        private final String mimeType;
        private final String message;

        private ReplacementPayload(boolean success, byte[] bytes, String originalName, String mimeType, String message) {
            this.success = success;
            this.bytes = bytes;
            this.originalName = originalName;
            this.mimeType = mimeType;
            this.message = message;
        }

        private static ReplacementPayload success(byte[] bytes, String originalName, String mimeType) {
            return new ReplacementPayload(true, bytes, originalName, mimeType, "");
        }

        private static ReplacementPayload fail(String message) {
            return new ReplacementPayload(false, new byte[0], "", "", message);
        }
    }
}
