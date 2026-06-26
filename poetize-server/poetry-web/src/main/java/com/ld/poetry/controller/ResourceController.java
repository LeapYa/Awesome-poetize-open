package com.ld.poetry.controller;


import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ld.poetry.aop.AuditLog;
import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.dao.ResourceMapper;
import com.ld.poetry.entity.Resource;
import com.ld.poetry.enums.PoetryEnum;
import com.ld.poetry.service.ResourceAvailabilityService;
import com.ld.poetry.service.ResourceReplaceService;
import com.ld.poetry.service.ResourceService;
import com.ld.poetry.service.ResourceThumbnailService;
import com.ld.poetry.utils.storage.StoreService;
import com.ld.poetry.utils.*;
import com.ld.poetry.utils.storage.FileStorageService;
import com.ld.poetry.utils.storage.StoreEnum;
import com.ld.poetry.utils.image.ImageCompressUtil;
import com.ld.poetry.utils.image.IcoConvertUtil;
import com.ld.poetry.utils.security.FileDownloadUtil;
import com.ld.poetry.utils.security.FileSecurityValidator;
import com.ld.poetry.vo.BaseRequestVO;
import com.ld.poetry.vo.FileVO;
import com.ld.poetry.vo.ResourceScanTaskVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.transaction.annotation.Transactional;

/**
 * <p>
 * 资源信息 前端控制器
 * </p>
 *
 * @author sara
 * @since 2022-03-06
 */
@SuppressWarnings("unchecked")
@RestController
@RequestMapping("/resource")
@Slf4j
public class ResourceController {

    private static final Set<String> SEO_ICON_TYPES = Set.of(
            "seoSiteIcon",
            "seoFavicon",
            "seoAppleTouchIcon",
            "seoSiteIcon192",
            "seoSiteIcon512",
            "seoApple-touch-icon",
            "seoIcon-192",
            "seoIcon-512",
            "seoLogo",
            "seoSiteLogo"
    );

    private static final Set<String> SEO_ICON_PATH_MARKERS = Set.of(
            "seositeicon",
            "seofavicon",
            "seoappletouchicon",
            "seositeicon192",
            "seositeicon512",
            "seoapple-touch-icon",
            "seoicon-192",
            "seoicon-512",
            "seologo",
            "seositelogo"
    );

    private static final String DEFAULT_RESOURCE_ORDER = "createTime";
    private static final String CHUNK_UPLOAD_DIR = ".chunks";
    private static final int MAX_CHUNK_COUNT = 20000;

    private static final Map<String, String> RESOURCE_ORDER_COLUMNS = Map.of(
            "id", "id",
            "originalName", "original_name",
            "userId", "user_id",
            "type", "type",
            "status", "status",
            "path", "path",
            "size", "size",
            "mimeType", "mime_type",
            "storeType", "store_type",
            "createTime", "create_time"
    );

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private ResourceMapper resourceMapper;

    @Autowired
    private ResourceAvailabilityService resourceAvailabilityService;

    @Autowired
    private ResourceReplaceService resourceReplaceService;

    @Autowired
    private ResourceThumbnailService resourceThumbnailService;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private FileSecurityValidator fileSecurityValidator;

    @Value("${local.uploadUrl:/app/static/}")
    private String localUploadUrl;

    @Value("${local.downloadUrl:/static/}")
    private String localDownloadUrl;

    /**
     * 保存
     */
    @PostMapping("/saveResource")
    @LoginCheck
    @AuditLog(action = "RESOURCE_SAVE", targetType = "RESOURCE", targetIdParam = "path", summary = "保存资源信息")
    public PoetryResult saveResource(@RequestBody Resource resource) {
        if (!StringUtils.hasText(resource.getType()) || !StringUtils.hasText(resource.getPath())) {
            return PoetryResult.fail("资源类型和资源路径不能为空！");
        }
        if (isResourceFilterType(resource.getType())) {
            return PoetryResult.fail(resourceFilterTypeName(resource.getType()) + "是筛选视图，不能作为资源类型保存！");
        }
        
        // 检查文件大小是否超过Integer.MAX_VALUE，防止溢出
        if (resource.getSize() != null && resource.getSize() > Integer.MAX_VALUE) {
            log.error("资源大小超过系统限制: {} bytes, 最大允许: {} bytes", resource.getSize(), Integer.MAX_VALUE);
            return PoetryResult.fail("资源大小超过系统限制(" + (Integer.MAX_VALUE / 1024 / 1024) + "MB)，请使用较小的文件");
        }
        
        Resource re = new Resource();
        re.setPath(resource.getPath());
        re.setType(resource.getType());
        re.setSize(resource.getSize());
        re.setOriginalName(resource.getOriginalName());
        re.setMimeType(resource.getMimeType());
        re.setResourceHash(resource.getResourceHash());
        re.setStoreType(resource.getStoreType());
        re.setUserId(PoetryUtil.getUserId());
        
        try {
            // 先查询是否已存在相同路径的资源
            Resource existingResource = resourceService.lambdaQuery()
                .eq(Resource::getPath, resource.getPath())
                .one();
            
            if (existingResource != null) {
                // 如果存在，更新资源信息
                existingResource.setType(resource.getType());
                existingResource.setSize(resource.getSize());
                existingResource.setOriginalName(resource.getOriginalName());
                existingResource.setMimeType(resource.getMimeType());
                existingResource.setResourceHash(resource.getResourceHash());
                existingResource.setStoreType(resource.getStoreType());
                existingResource.setUserId(PoetryUtil.getUserId());
                resourceService.updateById(existingResource);
            } else {
                // 不存在则保存新记录
        resourceService.save(re);
            }
        } catch (Exception e) {
            log.error("保存资源信息失败: {}", e.getMessage(), e);
            return PoetryResult.fail("保存资源信息失败: " + e.getMessage());
        }
        
        return PoetryResult.success();
    }

    /**
     * 上传文件（支持智能图片压缩）
     */
    @PostMapping("/upload")
    @LoginCheck
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(action = "RESOURCE_UPLOAD", targetType = "RESOURCE", targetIdParam = "relativePath", summary = "上传资源")
    public synchronized PoetryResult<String> upload(@RequestParam("file") MultipartFile file, FileVO fileVO) {
        if (file == null || !StringUtils.hasText(fileVO.getType()) || !StringUtils.hasText(fileVO.getRelativePath())) {
            return PoetryResult.fail("文件和资源类型和资源路径不能为空！");
        }
        if (isResourceFilterType(fileVO.getType())) {
            return PoetryResult.fail(resourceFilterTypeName(fileVO.getType()) + "是筛选视图，不能作为资源类型上传！");
        }

        try {
            // 验证文件安全性
            FileSecurityValidator.ValidationResult validationResult =
                    fileSecurityValidator.validateFile(file, file.getOriginalFilename(), file.getContentType());

            if (!validationResult.isSuccess()) {
                log.warn("文件安全验证失败: {}, 用户ID: {}", validationResult.getMessage(), PoetryUtil.getUserId());
                return PoetryResult.fail("文件验证失败: " + validationResult.getMessage());
            }

            log.info("文件安全验证通过: {}, Content-Type: {}", file.getOriginalFilename(), file.getContentType());
            if (FileDownloadUtil.shouldForceDownload(fileVO.getRelativePath())
                    && !CommonConst.PATH_TYPE_ARTICLE_FILE.equals(fileVO.getType())) {
                return PoetryResult.fail("该文件类型只能作为文章附件上传");
            }

            MultipartFile processedFile = file;
            String originalFileName = file.getOriginalFilename();
            long originalSize = file.getSize();

            // 尝试智能压缩（仅对图片有效）
            // 网站标签页图标：无论上传 PNG/JPEG/WebP 等，统一转为 ICO 便于浏览器兼容
            // 包含手动上传(seoSiteIcon)与智能图标生成后上传(seoFavicon)
            boolean isSiteFavicon = isSiteFaviconUpload(fileVO);
            boolean isSeoIcon = isSeoIconUpload(fileVO);
            if (isSiteFavicon) {
                byte[] icoBytes = IcoConvertUtil.convertToIco(file);
                if (icoBytes == null || icoBytes.length == 0) {
                    log.warn("网站图标转 ICO 失败，将按原文件保存: {}", originalFileName);
                } else {
                    processedFile = new CompressedMultipartFile(
                            file.getName(),
                            buildPngOrIcoFileName(originalFileName, "favicon.ico", "ico"),
                            "image/x-icon",
                            icoBytes
                    );
                    String oldRelativePath = fileVO.getRelativePath();
                    String newRelativePath = updateExtension(oldRelativePath, "ico");
                    if (!oldRelativePath.equals(newRelativePath)) {
                        fileVO.setRelativePath(newRelativePath);
                        log.info("网站图标已转为 ICO，路径: {} -> {}", oldRelativePath, newRelativePath);
                    }
                }
            } else if (isSeoIcon) {
                int targetSize = getSeoIconTargetSize(fileVO);
                if (targetSize <= 0) {
                    log.warn("未知的SEO图标类型，无法处理: type={}, path={}", fileVO.getType(), fileVO.getRelativePath());
                    return PoetryResult.fail("不支持的SEO图标类型");
                }

                byte[] pngBytes = IcoConvertUtil.convertToPngIcon(file, targetSize);
                if (pngBytes == null || pngBytes.length == 0) {
                    log.warn("SEO图标转 PNG 失败: {}", originalFileName);
                    return PoetryResult.fail("SEO图标处理失败，请上传 PNG/JPG 或确认 dwebp 已安装");
                }

                processedFile = new CompressedMultipartFile(
                        file.getName(),
                        buildPngOrIcoFileName(originalFileName, "icon.png", "png"),
                        "image/png",
                        pngBytes
                );

                String oldRelativePath = fileVO.getRelativePath();
                String newRelativePath = updateExtension(oldRelativePath, "png");
                if (!oldRelativePath.equals(newRelativePath)) {
                    fileVO.setRelativePath(newRelativePath);
                    log.info("SEO图标已转为 PNG，路径: {} -> {}", oldRelativePath, newRelativePath);
                }
            } else {
                try {
                    ImageCompressUtil.CompressResult compressResult = ImageCompressUtil.smartCompress(file);

                    // 创建压缩后的文件对象
                    processedFile = new CompressedMultipartFile(
                            file.getName(),
                            originalFileName,
                            compressResult.getContentType(),
                            compressResult.getData()
                    );

                    // 如果压缩后格式发生改变，更新文件路径的扩展名
                    String newExtension = getExtensionFromContentType(compressResult.getContentType());
                    if (!newExtension.isEmpty()) {
                        String oldRelativePath = fileVO.getRelativePath();
                        String newRelativePath = updateExtension(oldRelativePath, newExtension);
                        if (!oldRelativePath.equals(newRelativePath)) {
                            fileVO.setRelativePath(newRelativePath);
                            log.info("文件已转换格式，更新路径: {} -> {}", oldRelativePath, newRelativePath);
                        }
                    }

                } catch (IOException e) {
                    // 压缩失败时使用原文件（非图片文件会走到这里）
                }
            }

            // 在存储前检查文件大小是否超过Integer.MAX_VALUE，防止溢出
            long fileSize = processedFile.getSize();
            if (fileSize > Integer.MAX_VALUE) {
                log.error("文件大小超过系统限制: {} bytes, 最大允许: {} bytes", fileSize, Integer.MAX_VALUE);
                return PoetryResult.fail("文件大小超过系统限制(" + (Integer.MAX_VALUE / 1024 / 1024) + "MB)，请上传较小的文件");
            }

            if (CommonConst.PATH_TYPE_ARTICLE_FILE.equals(fileVO.getType())) {
                fileVO.setStoreType(StoreEnum.LOCAL.getCode());
            }

            fileVO.setFile(processedFile);
            StoreService storeService = fileStorageService.getFileStorage(fileVO.getStoreType());
            FileVO result = storeService.saveFile(fileVO);
            if (Boolean.TRUE.equals(result.getReuseExistingResource())) {
                return PoetryResult.success(result.getVisitPath());
            }
            // log.info("文件上传成功 - 路径: {}", result.getVisitPath());

            Resource re = new Resource();
            re.setPath(result.getVisitPath());
            re.setType(fileVO.getType());
            re.setSize(Integer.valueOf(Long.toString(fileSize)));
            re.setMimeType(processedFile.getContentType());
            re.setResourceHash(result.getResourceHash());
            re.setStoreType(result.getStoreType());
            re.setOriginalName(fileVO.getOriginalName());
            re.setUserId(PoetryUtil.getUserId());
            // 读取图片宽高并写入资源记录
            int[] dims = readImageDimensions(processedFile.getBytes());
            if (dims != null) {
                re.setWidth(dims[0]);
                re.setHeight(dims[1]);
            }

            // 先查询是否已存在相同路径的资源
            Resource existingResource = resourceService.lambdaQuery()
                .eq(Resource::getPath, result.getVisitPath())
                .one();
            
            if (existingResource != null) {
                // 如果存在，更新资源信息
                existingResource.setType(fileVO.getType());
                existingResource.setSize(Integer.valueOf(Long.toString(fileSize)));
                existingResource.setOriginalName(fileVO.getOriginalName());
                existingResource.setMimeType(processedFile.getContentType());
                existingResource.setResourceHash(result.getResourceHash());
                existingResource.setStoreType(result.getStoreType());
                existingResource.setUserId(PoetryUtil.getUserId());
                if (dims != null) {
                    existingResource.setWidth(dims[0]);
                    existingResource.setHeight(dims[1]);
                }
                resourceService.updateById(existingResource);
            } else {
                // 不存在则保存新记录
                resourceService.save(re);
            }
            
            return PoetryResult.success(result.getVisitPath());
            
        } catch (Exception e) {
            log.error("文件上传失败: {}", e.getMessage(), e);
            return PoetryResult.fail("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 智能图片压缩上传（专用接口）
     */
    @PostMapping("/uploadImageWithCompress")
    @LoginCheck
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(action = "RESOURCE_IMAGE_UPLOAD", targetType = "RESOURCE", targetIdParam = "relativePath", summary = "上传压缩图片")
    public synchronized PoetryResult<Object> uploadImageWithCompress(
            @RequestParam("file") MultipartFile file,
            FileVO fileVO,
            @RequestParam(value = "maxWidth", defaultValue = "1920") int maxWidth,
            @RequestParam(value = "maxHeight", defaultValue = "1080") int maxHeight,
            @RequestParam(value = "quality", defaultValue = "0.85") float quality,
            @RequestParam(value = "targetSize", defaultValue = "512000") long targetSize) {

        if (file == null || !StringUtils.hasText(fileVO.getType()) || !StringUtils.hasText(fileVO.getRelativePath())) {
            return PoetryResult.fail("文件和资源类型和资源路径不能为空！");
        }
        if (isResourceFilterType(fileVO.getType())) {
            return PoetryResult.fail(resourceFilterTypeName(fileVO.getType()) + "是筛选视图，不能作为资源类型上传！");
        }

        try {
            // 验证文件安全性
            FileSecurityValidator.ValidationResult validationResult =
                    fileSecurityValidator.validateFile(file, file.getOriginalFilename(), file.getContentType());

            if (!validationResult.isSuccess()) {
                log.warn("文件安全验证失败: {}, 用户ID: {}", validationResult.getMessage(), PoetryUtil.getUserId());
                return PoetryResult.fail("文件验证失败: " + validationResult.getMessage());
            }

            log.info("智能压缩上传 - 文件安全验证通过: {}, Content-Type: {}", file.getOriginalFilename(), file.getContentType());

            // 执行智能压缩
            ImageCompressUtil.CompressResult compressResult =
                    ImageCompressUtil.smartCompress(file, maxWidth, maxHeight, quality, targetSize);

            // 创建压缩后的文件
            MultipartFile compressedFile = new CompressedMultipartFile(
                    file.getName(),
                    file.getOriginalFilename(),
                    compressResult.getContentType(),
                    compressResult.getData()
            );

            // 如果压缩后格式发生改变，更新文件路径的扩展名
            String newExtension = getExtensionFromContentType(compressResult.getContentType());
            if (!newExtension.isEmpty()) {
                String oldRelativePath = fileVO.getRelativePath();
                String newRelativePath = updateExtension(oldRelativePath, newExtension);
                if (!oldRelativePath.equals(newRelativePath)) {
                    fileVO.setRelativePath(newRelativePath);
                    log.info("文件已转换格式，更新路径: {} -> {}", oldRelativePath, newRelativePath);
                }
            }

            // 在存储前检查压缩后文件大小是否超过Integer.MAX_VALUE，防止溢出
            long fileSize = compressedFile.getSize();
            if (fileSize > Integer.MAX_VALUE) {
                log.error("压缩后文件大小超过系统限制: {} bytes, 最大允许: {} bytes", fileSize, Integer.MAX_VALUE);
                return PoetryResult.fail("压缩后文件大小超过系统限制(" + (Integer.MAX_VALUE / 1024 / 1024) + "MB)，请调整压缩参数");
            }

            fileVO.setFile(compressedFile);
            StoreService storeService = fileStorageService.getFileStorage(fileVO.getStoreType());
            FileVO result = storeService.saveFile(fileVO);

            if (Boolean.TRUE.equals(result.getReuseExistingResource())) {
                return PoetryResult.success(new Object() {
                    public final String visitPath = result.getVisitPath();
                    public final long originalSize = compressResult.getOriginalSize();
                    public final long compressedSize = compressResult.getCompressedSize();
                    public final double compressionRatio = compressResult.getCompressionRatio();
                    public final String contentType = compressResult.getContentType();
                    public final boolean reused = true;
                });
            }

            Resource re = new Resource();
            re.setPath(result.getVisitPath());
            re.setType(fileVO.getType());
            re.setSize(Integer.valueOf(Long.toString(fileSize)));
            re.setMimeType(compressedFile.getContentType());
            re.setResourceHash(result.getResourceHash());
            re.setStoreType(result.getStoreType());
            re.setOriginalName(fileVO.getOriginalName());
            re.setUserId(PoetryUtil.getUserId());
            
            // 先查询是否已存在相同路径的资源
            Resource existingResource = resourceService.lambdaQuery()
                .eq(Resource::getPath, result.getVisitPath())
                .one();
            
            if (existingResource != null) {
                // 如果存在，更新资源信息
                existingResource.setType(fileVO.getType());
                existingResource.setSize(Integer.valueOf(Long.toString(fileSize)));
                existingResource.setOriginalName(fileVO.getOriginalName());
                existingResource.setMimeType(compressedFile.getContentType());
                existingResource.setResourceHash(result.getResourceHash());
                existingResource.setStoreType(result.getStoreType());
                existingResource.setUserId(PoetryUtil.getUserId());
                resourceService.updateById(existingResource);
            } else {
                // 不存在则保存新记录
            resourceService.save(re);
            }

            log.info("智能压缩上传成功 - 路径: {}, 压缩率: {:.1f}%", 
                    result.getVisitPath(), compressResult.getCompressionRatio());

            // 返回详细的压缩信息
            return PoetryResult.success(new Object() {
                public final String visitPath = result.getVisitPath();
                public final long originalSize = compressResult.getOriginalSize();
                public final long compressedSize = compressResult.getCompressedSize();
                public final double compressionRatio = compressResult.getCompressionRatio();
                public final String contentType = compressResult.getContentType();
            });
            
        } catch (Exception e) {
            log.error("智能压缩上传失败: {}", e.getMessage(), e);
            return PoetryResult.fail("智能压缩上传失败: " + e.getMessage());
        }
    }

    /**
     * 上传看板娘模型预览图（管理员专用）
     */
    @PostMapping("/uploadWaifuPreview")
    @LoginCheck(0)
    @AuditLog(action = "RESOURCE_WAIFU_PREVIEW_UPLOAD", targetType = "RESOURCE", summary = "上传看板娘预览图")
    public PoetryResult<String> uploadWaifuPreview(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return PoetryResult.fail("请选择要上传的图片！");
        }
        
        try {
            // 验证是图片文件
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return PoetryResult.fail("只能上传图片文件！");
            }

            FileSecurityValidator.ValidationResult validationResult =
                    fileSecurityValidator.validateFile(file, file.getOriginalFilename(), file.getContentType());
            if (!validationResult.isSuccess()) {
                return PoetryResult.fail("图片安全校验失败: " + validationResult.getMessage());
            }

            // 验证文件大小（2MB限制）
            if (file.getSize() > 2 * 1024 * 1024) {
                return PoetryResult.fail("图片大小不能超过2MB！");
            }

            // 生成唯一文件名
            String originalFilename = file.getOriginalFilename();
            String extension = StringUtils.hasText(validationResult.getExtension())
                    ? "." + validationResult.getExtension()
                    : ".png";
            String fileName = "waifu_preview_" + System.currentTimeMillis() + extension;
            
            // 使用FileVO存储
            FileVO fileVO = new FileVO();
            fileVO.setFile(file);
            fileVO.setType("waifuPreview");
            fileVO.setRelativePath("waifu_previews/" + fileName);
            fileVO.setOriginalName(originalFilename);
            
            StoreService storeService = fileStorageService.getFileStorage(fileVO.getStoreType());
            FileVO result = storeService.saveFile(fileVO);
            
            log.info("看板娘预览图上传成功: {}", result.getVisitPath());
            return PoetryResult.success(result.getVisitPath());
            
        } catch (Exception e) {
            log.error("看板娘预览图上传失败: {}", e.getMessage(), e);
            return PoetryResult.fail("上传失败: " + e.getMessage());
        }
    }

    /**
     * 删除
     */
    @PostMapping("/deleteResource")
    @LoginCheck(0)
    @AuditLog(action = "RESOURCE_DELETE", targetType = "RESOURCE", targetIdParam = "path", summary = "删除资源")
    public PoetryResult deleteResource(@RequestParam("path") String path) {
        Resource resource = resourceService.lambdaQuery().select(Resource::getStoreType).eq(Resource::getPath, path).one();
        if (resource == null) {
            return PoetryResult.fail("文件不存在：" + path);
        }

        StoreService storeService = fileStorageService.getFileStorageByStoreType(resource.getStoreType());
        storeService.deleteFile(Collections.singletonList(path));
        return PoetryResult.success();
    }

    /**
     * 原路径替换资源文件。
     */
    @PostMapping("/replaceResource")
    @LoginCheck(0)
    @AuditLog(action = "RESOURCE_REPLACE", targetType = "RESOURCE", targetIdParam = "id", summary = "替换资源")
    public PoetryResult<Resource> replaceResource(@RequestParam("id") Integer id,
                                                  @RequestParam("expectedPath") String expectedPath,
                                                  @RequestParam("file") MultipartFile file) {
        return resourceReplaceService.replaceResource(id, expectedPath, file);
    }

    /**
     * 生成后台资源列表缩略图。只服务自动预览，原资源 URL 不变。
     */
    @GetMapping("/thumbnail")
    @LoginCheck(0)
    public void thumbnail(@RequestParam("id") Integer id,
                          @RequestParam(value = "w", defaultValue = "120") Integer width,
                          @RequestParam(value = "h", defaultValue = "104") Integer height,
                          @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch,
                          HttpServletResponse response) throws IOException {
        try {
            ResourceThumbnailService.Thumbnail thumbnail = resourceThumbnailService.createThumbnail(id, width, height);
            response.setHeader("Cache-Control", "private, max-age=86400");
            response.setHeader("ETag", thumbnail.getETag());
            response.setHeader("X-Content-Type-Options", "nosniff");
            if (thumbnail.getETag().equals(ifNoneMatch)) {
                response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                return;
            }

            response.setContentType(thumbnail.getContentType());
            response.setContentLength(thumbnail.getBytes().length);
            response.getOutputStream().write(thumbnail.getBytes());
        } catch (ResourceThumbnailService.ThumbnailException e) {
            response.sendError(e.getStatusCode(), e.getMessage());
        }
    }

    /**
     * 接收大文件分片。每个分片都是很小的 multipart 请求，避免被 Nginx 单请求大小限制拦截。
     */
    @PostMapping("/uploadChunk")
    @LoginCheck
    public PoetryResult<Boolean> uploadChunk(@RequestParam("chunk") MultipartFile chunk,
                                             @RequestParam("uploadId") String uploadId,
                                             @RequestParam("chunkIndex") int chunkIndex,
                                             @RequestParam("totalChunks") int totalChunks) {
        if (chunk == null || chunk.isEmpty()) {
            return PoetryResult.fail("上传分片不能为空！");
        }
        if (!isValidChunkUpload(uploadId, chunkIndex, totalChunks)) {
            return PoetryResult.fail("分片参数不合法！");
        }

        try {
            Path chunkDir = resolveChunkDir(uploadId);
            Files.createDirectories(chunkDir);
            Path chunkPath = chunkDir.resolve(formatChunkName(chunkIndex)).normalize();
            if (!chunkPath.startsWith(chunkDir)) {
                return PoetryResult.fail("分片路径不合法！");
            }
            try (InputStream inputStream = chunk.getInputStream()) {
                Files.copy(inputStream, chunkPath, StandardCopyOption.REPLACE_EXISTING);
            }
            return PoetryResult.success(true);
        } catch (Exception e) {
            log.error("上传分片失败: uploadId={}, chunkIndex={}", uploadId, chunkIndex, e);
            return PoetryResult.fail("上传分片失败: " + e.getMessage());
        }
    }

    /**
     * 合并分片并保存为资源。当前用于文章编辑器本地视频上传。
     */
    @PostMapping("/mergeChunks")
    @LoginCheck
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(action = "RESOURCE_CHUNKS_MERGE", targetType = "RESOURCE", targetIdParam = "relativePath", summary = "合并分片上传资源")
    public synchronized PoetryResult<String> mergeChunks(@RequestParam("uploadId") String uploadId,
                                                         @RequestParam("totalChunks") int totalChunks,
                                                         @RequestParam("originalName") String originalName,
                                                         @RequestParam("relativePath") String relativePath,
                                                         @RequestParam("type") String type,
                                                         @RequestParam(value = "contentType", required = false) String contentType) {
        if (!isValidChunkUpload(uploadId, 0, totalChunks)
                || !StringUtils.hasText(originalName)
                || !StringUtils.hasText(relativePath)
                || !StringUtils.hasText(type)) {
            return PoetryResult.fail("分片合并参数不合法！");
        }
        if (isResourceFilterType(type)) {
            return PoetryResult.fail(resourceFilterTypeName(type) + "是筛选视图，不能作为资源类型上传！");
        }

        Path chunkDir = null;
        Path mergedFile = null;
        try {
            chunkDir = resolveChunkDir(uploadId);
            if (!Files.isDirectory(chunkDir)) {
                return PoetryResult.fail("上传分片不存在或已过期！");
            }

            mergedFile = chunkDir.resolve("merged.uploading").normalize();
            if (!mergedFile.startsWith(chunkDir)) {
                return PoetryResult.fail("合并路径不合法！");
            }
            try (OutputStream outputStream = Files.newOutputStream(mergedFile)) {
                for (int i = 0; i < totalChunks; i++) {
                    Path chunkPath = chunkDir.resolve(formatChunkName(i)).normalize();
                    if (!chunkPath.startsWith(chunkDir) || !Files.isRegularFile(chunkPath)) {
                        return PoetryResult.fail("上传分片不完整，请重新上传！");
                    }
                    Files.copy(chunkPath, outputStream);
                }
            }

            MultipartFile mergedMultipartFile = new PathMultipartFile(
                    "file",
                    originalName,
                    StringUtils.hasText(contentType) ? contentType : MediaType.APPLICATION_OCTET_STREAM_VALUE,
                    mergedFile
            );
            FileVO fileVO = new FileVO();
            fileVO.setType(type);
            fileVO.setStoreType(StoreEnum.LOCAL.getCode());
            fileVO.setRelativePath(relativePath);
            fileVO.setOriginalName(originalName);
            return saveMergedLocalResource(mergedMultipartFile, fileVO);
        } catch (Exception e) {
            log.error("合并分片失败: uploadId={}", uploadId, e);
            return PoetryResult.fail("合并分片失败: " + e.getMessage());
        } finally {
            if (chunkDir != null) {
                deleteQuietly(chunkDir);
            } else if (mergedFile != null) {
                deleteQuietly(mergedFile);
            }
        }
    }

    private PoetryResult<String> saveMergedLocalResource(MultipartFile file, FileVO fileVO) {
        FileSecurityValidator.ValidationResult validationResult =
                fileSecurityValidator.validateFile(file, file.getOriginalFilename(), file.getContentType());
        if (!validationResult.isSuccess()) {
            log.warn("分片文件安全验证失败: {}, 用户ID: {}", validationResult.getMessage(), PoetryUtil.getUserId());
            return PoetryResult.fail("文件验证失败: " + validationResult.getMessage());
        }

        if (FileDownloadUtil.shouldForceDownload(fileVO.getRelativePath())
                && !CommonConst.PATH_TYPE_ARTICLE_FILE.equals(fileVO.getType())) {
            return PoetryResult.fail("该文件类型只能作为文章附件上传");
        }

        long fileSize = file.getSize();
        if (fileSize > Integer.MAX_VALUE) {
            log.error("文件大小超过系统限制: {} bytes, 最大允许: {} bytes", fileSize, Integer.MAX_VALUE);
            return PoetryResult.fail("文件大小超过系统限制(" + (Integer.MAX_VALUE / 1024 / 1024) + "MB)，请上传较小的文件");
        }

        fileVO.setFile(file);
        fileVO.setStoreType(StoreEnum.LOCAL.getCode());
        StoreService storeService = fileStorageService.getFileStorage(StoreEnum.LOCAL.getCode());
        FileVO result = storeService.saveFile(fileVO);
        if (Boolean.TRUE.equals(result.getReuseExistingResource())) {
            return PoetryResult.success(result.getVisitPath());
        }

        Resource resource = new Resource();
        resource.setPath(result.getVisitPath());
        resource.setType(fileVO.getType());
        resource.setSize(Integer.valueOf(Long.toString(fileSize)));
        resource.setMimeType(file.getContentType());
        resource.setResourceHash(result.getResourceHash());
        resource.setStoreType(result.getStoreType());
        resource.setOriginalName(fileVO.getOriginalName());
        resource.setUserId(PoetryUtil.getUserId());

        if (StringUtils.hasText(file.getContentType()) && file.getContentType().startsWith("image/")) {
            try {
                int[] dims = readImageDimensions(file.getBytes());
                if (dims != null) {
                    resource.setWidth(dims[0]);
                    resource.setHeight(dims[1]);
                }
            } catch (IOException e) {
                log.debug("读取分片合并图片尺寸失败: {}", e.getMessage());
            }
        }

        Resource existingResource = resourceService.lambdaQuery()
                .eq(Resource::getPath, result.getVisitPath())
                .one();
        if (existingResource != null) {
            existingResource.setType(resource.getType());
            existingResource.setSize(resource.getSize());
            existingResource.setOriginalName(resource.getOriginalName());
            existingResource.setMimeType(resource.getMimeType());
            existingResource.setResourceHash(resource.getResourceHash());
            existingResource.setStoreType(resource.getStoreType());
            existingResource.setUserId(resource.getUserId());
            existingResource.setWidth(resource.getWidth());
            existingResource.setHeight(resource.getHeight());
            resourceService.updateById(existingResource);
        } else {
            resourceService.save(resource);
        }

        return PoetryResult.success(result.getVisitPath());
    }

    private boolean isValidChunkUpload(String uploadId, int chunkIndex, int totalChunks) {
        return StringUtils.hasText(uploadId)
                && uploadId.matches("[A-Za-z0-9_-]{8,80}")
                && totalChunks > 0
                && totalChunks <= MAX_CHUNK_COUNT
                && chunkIndex >= 0
                && chunkIndex < totalChunks;
    }

    private Path resolveChunkDir(String uploadId) {
        Path chunkRoot = normalizeLocalUploadRoot()
                .resolve(CHUNK_UPLOAD_DIR)
                .resolve(String.valueOf(PoetryUtil.getUserId()))
                .normalize();
        Path chunkDir = chunkRoot.resolve(uploadId).normalize();
        if (!chunkDir.startsWith(chunkRoot)) {
            throw new IllegalArgumentException("分片路径不合法");
        }
        return chunkDir;
    }

    private Path normalizeLocalUploadRoot() {
        String uploadPath = StringUtils.hasText(localUploadUrl) ? localUploadUrl : "/app/static/";
        if (uploadPath.startsWith("file:")) {
            uploadPath = uploadPath.substring("file:".length());
        }
        return Paths.get(uploadPath).toAbsolutePath().normalize();
    }

    private String formatChunkName(int chunkIndex) {
        return String.format("%06d.part", chunkIndex);
    }

    private void deleteQuietly(Path path) {
        try {
            if (path == null || !Files.exists(path)) {
                return;
            }
            if (Files.isDirectory(path)) {
                try (var stream = Files.walk(path)) {
                    stream.sorted(Comparator.reverseOrder()).forEach(item -> {
                        try {
                            Files.deleteIfExists(item);
                        } catch (IOException e) {
                            log.debug("清理分片文件失败: {}", item, e);
                        }
                    });
                }
            } else {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            log.debug("清理分片目录失败: {}", path, e);
        }
    }

    /**
     * 已登记资源的强制下载入口。
     */
    @GetMapping("/download")
    public void download(@RequestParam("path") String path,
                         @RequestParam(value = "filename", required = false) String filename,
                         HttpServletResponse response) throws IOException {
        downloadRegisteredResource(path, filename, response);
    }

    private void downloadRegisteredResource(String path, String filename, HttpServletResponse response) throws IOException {
        if (FileDownloadUtil.hasUnsafeDownloadPath(path)
                || isRemoteResource(path)
                || !isLocalArticleFilePath(path)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        Resource resource = resourceService.lambdaQuery()
                .eq(Resource::getPath, path)
                .last("limit 1")
                .one();
        if (resource == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String downloadName = FileDownloadUtil.sanitizeFileName(filename);
        if (!StringUtils.hasText(downloadName)) {
            downloadName = FileDownloadUtil.sanitizeFileName(resource.getOriginalName());
        }
        if (!StringUtils.hasText(downloadName)) {
            downloadName = FileDownloadUtil.fileNameFromPath(path);
        }

        response.setHeader("Content-Disposition", FileDownloadUtil.contentDispositionAttachment(downloadName));
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);

        streamLocalResource(path, response);
    }

    /**
     * 查询表情包
     */
    @GetMapping("/getImageList")
    @LoginCheck
    public PoetryResult<List<String>> getImageList() {
        List<Resource> list = resourceService.lambdaQuery().select(Resource::getPath)
                .eq(Resource::getType, CommonConst.PATH_TYPE_INTERNET_MEME)
                .eq(Resource::getStatus, PoetryEnum.STATUS_ENABLE.getCode())
                .eq(Resource::getUserId, PoetryUtil.getAdminUser().getId())
                .orderByDesc(Resource::getCreateTime)
                .list();
        List<String> paths = list.stream().map(Resource::getPath).collect(Collectors.toList());
        return PoetryResult.success(paths);
    }

    /**
     * 查询资源
     */
    @PostMapping("/listResource")
    @LoginCheck(0)
    public PoetryResult<Page> listResource(@RequestBody BaseRequestVO baseRequestVO) {
        Page<Resource> page = new Page<>(baseRequestVO.getCurrent(), baseRequestVO.getSize());
        String order = resolveResourceOrder(baseRequestVO.getOrder());
        String orderColumn = RESOURCE_ORDER_COLUMNS.get(order);
        boolean asc = !baseRequestVO.isDesc();
        if (isOrphanResourceFilterType(baseRequestVO.getResourceType())) {
            page = resourceMapper.selectOrphanResources(page, List.of(CommonConst.PATH_TYPE_ASSETS), orderColumn, asc);
        } else if (isInvalidResourceFilterType(baseRequestVO.getResourceType())) {
            // 优先使用异步任务的缓存结果，无缓存时回退同步检测
            Page<Resource> cached = resourceAvailabilityService.listInvalidResourcesFromCache(page, order, asc);
            page = cached != null ? cached : resourceAvailabilityService.listInvalidResources(page, order, asc);
        } else {
            LambdaQueryChainWrapper<Resource> query = resourceService.lambdaQuery()
                    .eq(StringUtils.hasText(baseRequestVO.getResourceType()), Resource::getType, baseRequestVO.getResourceType());
            applyResourceOrder(query, order, asc);
            query.page(page);
        }
        baseRequestVO.setRecords(page.getRecords());
        baseRequestVO.setTotal(page.getTotal());
        return PoetryResult.success(baseRequestVO);
    }

    /**
     * 启动无效资源异步检测任务
     * @return 任务VO（含taskId，前端用taskId轮询进度）
     */
    @PostMapping("/startInvalidScan")
    @LoginCheck(0)
    @AuditLog(action = "RESOURCE_INVALID_SCAN_START", targetType = "RESOURCE", summary = "启动无效资源检测")
    public PoetryResult<ResourceScanTaskVO> startInvalidScan(@RequestBody BaseRequestVO baseRequestVO) {
        String order = resolveResourceOrder(baseRequestVO.getOrder());
        boolean asc = !baseRequestVO.isDesc();
        ResourceScanTaskVO task = resourceAvailabilityService.startInvalidResourceScanTask(order, asc);
        return PoetryResult.success(task);
    }

    /**
     * 查询检测任务状态
     */
    @GetMapping("/scanStatus")
    @LoginCheck(0)
    @AuditLog(action = "RESOURCE_SCAN_STATUS_QUERY", targetType = "RESOURCE", targetIdParam = "taskId", summary = "查询资源检测任务状态")
    public PoetryResult<ResourceScanTaskVO> getScanStatus(@RequestParam("taskId") String taskId) {
        ResourceScanTaskVO task = resourceAvailabilityService.getTask(taskId);
        if (task == null) {
            return PoetryResult.fail("任务不存在或已过期");
        }
        return PoetryResult.success(task);
    }

    /**
     * 取消检测任务
     */
    @GetMapping("/cancelScan")
    @LoginCheck(0)
    @AuditLog(action = "RESOURCE_SCAN_CANCEL", targetType = "RESOURCE", targetIdParam = "taskId", summary = "取消资源检测任务")
    public PoetryResult<Boolean> cancelScan(@RequestParam("taskId") String taskId) {
        boolean ok = resourceAvailabilityService.cancelScanTask(taskId);
        return PoetryResult.success(ok);
    }

    /**
     * 修改资源状态
     */
    @GetMapping("/changeResourceStatus")
    @LoginCheck(0)
    @AuditLog(action = "RESOURCE_STATUS_CHANGE", targetType = "RESOURCE", targetIdParam = "id", summary = "修改资源状态")
    public PoetryResult changeResourceStatus(@RequestParam("id") Integer id, @RequestParam("flag") Boolean flag) {
        resourceService.lambdaUpdate().eq(Resource::getId, id).set(Resource::getStatus, flag).update();
        return PoetryResult.success();
    }

    private void streamLocalResource(String resourcePath, HttpServletResponse response) throws IOException {
        Path basePath = Paths.get(normalizeLocalUploadUrl()).toAbsolutePath().normalize();
        String relativePath = resourcePath.replace('\\', '/');
        if (StringUtils.hasText(localDownloadUrl) && relativePath.startsWith(localDownloadUrl)) {
            relativePath = relativePath.substring(localDownloadUrl.length());
        } else if (relativePath.startsWith("/static/")) {
            relativePath = relativePath.substring("/static/".length());
        }
        while (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }

        Path filePath = basePath.resolve(relativePath.replace("/", File.separator)).normalize();
        if (!filePath.startsWith(basePath) || !Files.isRegularFile(filePath)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        response.setContentLengthLong(Files.size(filePath));
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            inputStream.transferTo(response.getOutputStream());
        }
    }

    private boolean isOrphanResourceFilterType(String resourceType) {
        return CommonConst.PATH_TYPE_ORPHAN_RESOURCE.equals(resourceType);
    }

    private boolean isInvalidResourceFilterType(String resourceType) {
        return CommonConst.PATH_TYPE_INVALID_RESOURCE.equals(resourceType);
    }

    private boolean isResourceFilterType(String resourceType) {
        return isOrphanResourceFilterType(resourceType) || isInvalidResourceFilterType(resourceType);
    }

    private String resolveResourceOrder(String order) {
        if (!StringUtils.hasText(order)) {
            return DEFAULT_RESOURCE_ORDER;
        }
        String normalizedOrder = order.trim();
        return RESOURCE_ORDER_COLUMNS.containsKey(normalizedOrder) ? normalizedOrder : DEFAULT_RESOURCE_ORDER;
    }

    private void applyResourceOrder(LambdaQueryChainWrapper<Resource> query, String order, boolean asc) {
        switch (order) {
            case "id":
                query.orderBy(true, asc, Resource::getId);
                break;
            case "originalName":
                query.orderBy(true, asc, Resource::getOriginalName);
                break;
            case "userId":
                query.orderBy(true, asc, Resource::getUserId);
                break;
            case "type":
                query.orderBy(true, asc, Resource::getType);
                break;
            case "status":
                query.orderBy(true, asc, Resource::getStatus);
                break;
            case "path":
                query.orderBy(true, asc, Resource::getPath);
                break;
            case "size":
                query.orderBy(true, asc, Resource::getSize);
                break;
            case "mimeType":
                query.orderBy(true, asc, Resource::getMimeType);
                break;
            case "storeType":
                query.orderBy(true, asc, Resource::getStoreType);
                break;
            case "createTime":
            default:
                query.orderBy(true, asc, Resource::getCreateTime);
                break;
        }
        if (!"id".equals(order)) {
            query.orderBy(true, asc, Resource::getId);
        }
    }

    private String resourceFilterTypeName(String resourceType) {
        if (isInvalidResourceFilterType(resourceType)) {
            return "无效资源";
        }
        return "孤儿资源";
    }

    private boolean isRemoteResource(String resourcePath) {
        if (!StringUtils.hasText(resourcePath)) {
            return false;
        }
        String lowerPath = resourcePath.toLowerCase();
        return lowerPath.startsWith("http://") || lowerPath.startsWith("https://");
    }

    private boolean isLocalArticleFilePath(String resourcePath) {
        if (!StringUtils.hasText(resourcePath)) {
            return false;
        }
        String normalized = resourcePath.replace('\\', '/');
        if (normalized.startsWith("/static/articleFile/")) {
            return true;
        }

        if (!StringUtils.hasText(localDownloadUrl)) {
            return false;
        }
        String localPrefix = localDownloadUrl.replace('\\', '/');
        if (!localPrefix.endsWith("/")) {
            localPrefix += "/";
        }
        return localPrefix.startsWith("/") && normalized.startsWith(localPrefix + "articleFile/");
    }

    private String normalizeLocalUploadUrl() {
        String uploadPath = localUploadUrl;
        if (uploadPath.startsWith("file:")) {
            uploadPath = uploadPath.substring("file:".length());
        }
        return uploadPath;
    }

    /**
     * 根据ContentType获取对应的文件扩展名
     */
    private String getExtensionFromContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        if (contentType.contains("webp")) {
            return "webp";
        } else if (contentType.contains("x-icon") || contentType.contains("ico")) {
            return "ico";
        } else if (contentType.contains("jpeg") || contentType.contains("jpg")) {
            return "jpg";
        } else if (contentType.contains("png")) {
            return "png";
        } else if (contentType.contains("gif")) {
            return "gif";
        }
        return "";
    }

    private boolean isSiteFaviconUpload(FileVO fileVO) {
        if (fileVO == null) {
            return false;
        }
        String type = fileVO.getType();
        if ("seoSiteIcon".equals(type) || "seoFavicon".equals(type)) {
            return true;
        }
        String relativePath = fileVO.getRelativePath();
        if (StringUtils.hasText(relativePath)) {
            String lowerPath = relativePath.toLowerCase();
            if (lowerPath.contains("seoappletouchicon")
                    || lowerPath.contains("seositeicon192")
                    || lowerPath.contains("seositeicon512")) {
                return false;
            }
            return lowerPath.contains("seositeicon") || lowerPath.contains("seofavicon");
        }
        return false;
    }

    private boolean isSeoIconUpload(FileVO fileVO) {
        if (fileVO == null) {
            return false;
        }
        String type = fileVO.getType();
        if (StringUtils.hasText(type) && SEO_ICON_TYPES.contains(type)) {
            return true;
        }
        String relativePath = fileVO.getRelativePath();
        if (StringUtils.hasText(relativePath)) {
            String lowerPath = relativePath.toLowerCase();
            for (String marker : SEO_ICON_PATH_MARKERS) {
                if (lowerPath.contains(marker)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 更新文件路径的扩展名
     */
    private String updateExtension(String filePath, String newExtension) {
        if (filePath == null || filePath.isEmpty()) {
            return filePath;
        }

        // 移除原有扩展名
        int lastDotIndex = filePath.lastIndexOf('.');
        int lastSlashIndex = filePath.lastIndexOf('/');

        // 只有当点号在最后一个斜杠之后，才认为是文件扩展名
        if (lastDotIndex > lastSlashIndex) {
            String nameWithoutExt = filePath.substring(0, lastDotIndex);
            return nameWithoutExt + "." + newExtension;
        } else {
            // 没有扩展名，直接添加
            return filePath + "." + newExtension;
        }
    }

    private int getSeoIconTargetSize(FileVO fileVO) {
        if (fileVO == null) {
            return 0;
        }
        String type = fileVO.getType();
        if ("seoAppleTouchIcon".equals(type)) {
            return 180;
        }
        if ("seoApple-touch-icon".equals(type)) {
            return 180;
        }
        if ("seoSiteIcon192".equals(type)) {
            return 192;
        }
        if ("seoIcon-192".equals(type)) {
            return 192;
        }
        if ("seoSiteIcon512".equals(type)) {
            return 512;
        }
        if ("seoIcon-512".equals(type)) {
            return 512;
        }
        if ("seoLogo".equals(type) || "seoSiteLogo".equals(type)) {
            return 256;
        }
        String relativePath = fileVO.getRelativePath();
        if (StringUtils.hasText(relativePath)) {
            String lowerPath = relativePath.toLowerCase();
            if (lowerPath.contains("seoappletouchicon")) {
                return 180;
            }
            if (lowerPath.contains("seoapple-touch-icon")) {
                return 180;
            }
            if (lowerPath.contains("seositeicon192")) {
                return 192;
            }
            if (lowerPath.contains("seoicon-192")) {
                return 192;
            }
            if (lowerPath.contains("seositeicon512")) {
                return 512;
            }
            if (lowerPath.contains("seoicon-512")) {
                return 512;
            }
            if (lowerPath.contains("seologo") || lowerPath.contains("seositelogo")) {
                return 256;
            }
        }
        return 0;
    }

    private String buildPngOrIcoFileName(String originalFileName, String fallbackName, String extension) {
        if (!StringUtils.hasText(originalFileName)) {
            return fallbackName;
        }
        int dotIndex = originalFileName.lastIndexOf('.');
        String baseName = dotIndex > 0 ? originalFileName.substring(0, dotIndex) : originalFileName;
        return baseName + "." + extension;
    }


    /**
     * 批量查询图片宽高（前台文章渲染时调用）。
     * 请求体：{ "paths": ["http://...img1.jpg", "http://...img2.webp", ...] }
     * 响应：{ "data": { "http://...img1.jpg": {"width": 800, "height": 600}, ... } }
     * 无需登录，任何人均可访问（只读操作）。
     */
    @PostMapping("/imageDimensions")
    public PoetryResult<Map<String, Map<String, Integer>>> getImageDimensions(
            @RequestBody Map<String, List<String>> body) {
        List<String> paths = body == null ? null : body.get("paths");
        if (paths == null || paths.isEmpty()) {
            return PoetryResult.success(Collections.emptyMap());
        }
        // 防止单次请求过大
        if (paths.size() > 500) {
            paths = paths.subList(0, 500);
        }

        List<Resource> resources = resourceService.lambdaQuery()
                .in(Resource::getPath, paths)
                .select(Resource::getPath, Resource::getWidth, Resource::getHeight)
                .list();

        Map<String, Resource> resourceByPath = resources.stream()
                .collect(Collectors.toMap(Resource::getPath, r -> r, (a, b) -> a));

        Map<String, Map<String, Integer>> result = new ConcurrentHashMap<>();
        List<String> missingPaths = new ArrayList<>();

        for (String path : paths) {
            Resource r = resourceByPath.get(path);
            if (r != null && r.getWidth() != null && r.getHeight() != null) {
                result.put(path, buildDimensionMap(r.getWidth(), r.getHeight()));
                continue;
            }

            missingPaths.add(path);
        }

        if (!missingPaths.isEmpty()) {
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                List<Future<?>> futures = new ArrayList<>();
                for (String path : missingPaths) {
                    Resource resource = resourceByPath.get(path);
                    futures.add(executor.submit(() -> {
                        int[] resolvedDims = resolveAndPersistImageDimensions(path, resource);
                        if (resolvedDims != null) {
                            result.put(path, buildDimensionMap(resolvedDims[0], resolvedDims[1]));
                        }
                    }));
                }

                for (Future<?> future : futures) {
                    future.get();
                }
            } catch (Exception e) {
                log.debug("并发查询图片宽高失败: {}", e.getMessage());
            }
        }

        return PoetryResult.success(result);
    }

    /**
     * 从 byte[] 读取图片宽高，非图片或解析失败返回 null
     */
    private int[] readImageDimensions(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img == null) return null;
            return new int[]{img.getWidth(), img.getHeight()};
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 URL（或本地文件路径）读取图片宽高，失败返回 null（供回填使用）
     */
    private int[] readImageDimensionsFromUrl(String path) {
        if (!StringUtils.hasText(path)) return null;
        try {
            java.io.InputStream is;
            if (path.startsWith("http://") || path.startsWith("https://")) {
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                        java.net.URI.create(path).toURL().openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("User-Agent", "Poetize-Backfill/1.0");
                is = conn.getInputStream();
            } else {
                java.io.File file = new java.io.File(path);
                if (!file.exists() || !file.isFile()) return null;
                is = new java.io.FileInputStream(file);
            }
            try (is) {
                BufferedImage img = ImageIO.read(is);
                if (img == null) return null;
                return new int[]{img.getWidth(), img.getHeight()};
            }
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Integer> buildDimensionMap(int width, int height) {
        Map<String, Integer> dims = new HashMap<>();
        dims.put("width", width);
        dims.put("height", height);
        return dims;
    }

    /**
     * 首次命中旧文章图片时，现场解析图片尺寸并回写资源表。
     * 这样无需任何人工触发兼容流程，旧文章会在正常访问中自动完成迁移。
     */
    private int[] resolveAndPersistImageDimensions(String path, Resource existingResource) {
        if (!StringUtils.hasText(path)) {
            return null;
        }

        int[] dims = readImageDimensionsFromUrl(path);
        if (dims == null) {
            return null;
        }

        try {
            if (existingResource != null && existingResource.getId() != null) {
                Resource update = new Resource();
                update.setId(existingResource.getId());
                update.setWidth(dims[0]);
                update.setHeight(dims[1]);
                resourceService.updateById(update);
            } else {
                Resource matched = resourceService.lambdaQuery()
                        .eq(Resource::getPath, path)
                        .select(Resource::getId, Resource::getWidth, Resource::getHeight)
                        .one();
                if (matched != null && matched.getId() != null) {
                    Resource update = new Resource();
                    update.setId(matched.getId());
                    update.setWidth(dims[0]);
                    update.setHeight(dims[1]);
                    resourceService.updateById(update);
                }
            }
        } catch (Exception e) {
            log.debug("自动回填图片宽高失败: path={}, err={}", path, e.getMessage());
        }

        return dims;
    }

    /**
     * 自定义MultipartFile实现，用于压缩后的文件数据
     */
    private static class PathMultipartFile implements MultipartFile {
        private final String name;
        private final String originalFilename;
        private final String contentType;
        private final Path path;

        public PathMultipartFile(String name, String originalFilename, String contentType, Path path) {
            this.name = name;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.path = path;
        }

        @Override
        public String getName() {
            return this.name;
        }

        @Override
        public String getOriginalFilename() {
            return this.originalFilename;
        }

        @Override
        public String getContentType() {
            return this.contentType;
        }

        @Override
        public boolean isEmpty() {
            try {
                return Files.size(this.path) == 0;
            } catch (IOException e) {
                return true;
            }
        }

        @Override
        public long getSize() {
            try {
                return Files.size(this.path);
            } catch (IOException e) {
                return 0;
            }
        }

        @Override
        public byte[] getBytes() throws IOException {
            return Files.readAllBytes(this.path);
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return Files.newInputStream(this.path);
        }

        @Override
        public void transferTo(File dest) throws IOException, IllegalStateException {
            Files.copy(this.path, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static class CompressedMultipartFile implements MultipartFile {
        private final String name;
        private final String originalFilename;
        private final String contentType;
        private final byte[] content;

        public CompressedMultipartFile(String name, String originalFilename, String contentType, byte[] content) {
            this.name = name;
            this.contentType = contentType;
            this.content = content;
            this.originalFilename = originalFilename;
        }

        @Override
        public String getName() {
            return this.name;
        }

        @Override
        public String getOriginalFilename() {
            return this.originalFilename;
        }

        @Override
        public String getContentType() {
            return this.contentType;
        }

        @Override
        public boolean isEmpty() {
            return this.content.length == 0;
        }

        @Override
        public long getSize() {
            return this.content.length;
        }

        @Override
        public byte[] getBytes() throws IOException {
            return this.content;
        }

        @Override
        public java.io.InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(this.content);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(dest)) {
                fos.write(this.content);
            }
        }
    }
}
