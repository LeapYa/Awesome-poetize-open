package com.ld.poetry.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.entity.Resource;
import com.ld.poetry.enums.PoetryEnum;
import com.ld.poetry.service.ResourceService;
import com.ld.poetry.utils.storage.StoreService;
import com.ld.poetry.utils.*;
import com.ld.poetry.utils.storage.FileStorageService;
import com.ld.poetry.utils.image.ImageCompressUtil;
import com.ld.poetry.vo.BaseRequestVO;
import com.ld.poetry.vo.FileVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private FileStorageService fileStorageService;

    /**
     * 保存
     */
    @PostMapping("/saveResource")
    @LoginCheck
    public PoetryResult saveResource(@RequestBody Resource resource) {
        if (!StringUtils.hasText(resource.getType()) || !StringUtils.hasText(resource.getPath())) {
            return PoetryResult.fail("资源类型和资源路径不能为空！");
        }
        Resource re = new Resource();
        re.setPath(resource.getPath());
        re.setType(resource.getType());
        re.setSize(resource.getSize());
        re.setOriginalName(resource.getOriginalName());
        re.setMimeType(resource.getMimeType());
        re.setStoreType(resource.getStoreType());
        re.setUserId(PoetryUtil.getUserId());
        resourceService.save(re);
        return PoetryResult.success();
    }

    /**
     * 上传文件（支持智能图片压缩）
     */
    @PostMapping("/upload")
    @LoginCheck
    public PoetryResult<String> upload(@RequestParam("file") MultipartFile file, FileVO fileVO) {
        if (file == null || !StringUtils.hasText(fileVO.getType()) || !StringUtils.hasText(fileVO.getRelativePath())) {
            return PoetryResult.fail("文件和资源类型和资源路径不能为空！");
        }

        try {
            MultipartFile processedFile = file;
            String originalFileName = file.getOriginalFilename();
            long originalSize = file.getSize();
            
            // 检查是否为图片文件，如果是则进行压缩
            if (isImageFile(file.getContentType())) {
                log.info("检测到图片文件，开始压缩: {}, 原始大小: {}KB", 
                        originalFileName, originalSize / 1024);
                
                try {
                    ImageCompressUtil.CompressResult compressResult = ImageCompressUtil.smartCompress(file);
                    
                    // 创建压缩后的文件对象
                    processedFile = new CompressedMultipartFile(
                            file.getName(),
                            originalFileName,
                            compressResult.getContentType(),
                            compressResult.getData()
                    );
                    
                    log.info("图片压缩成功: {} -> {}KB, 压缩率: {:.1f}%", 
                            originalSize / 1024, 
                            compressResult.getCompressedSize() / 1024,
                            compressResult.getCompressionRatio());
                    
                } catch (IOException e) {
                    log.warn("图片压缩失败，使用原文件: {}", e.getMessage());
                    // 压缩失败时使用原文件
                }
            }

            fileVO.setFile(processedFile);
            StoreService storeService = fileStorageService.getFileStorage(fileVO.getStoreType());
            FileVO result = storeService.saveFile(fileVO);

            Resource re = new Resource();
            re.setPath(result.getVisitPath());
            re.setType(fileVO.getType());
            re.setSize(Integer.valueOf(Long.toString(processedFile.getSize())));
            re.setMimeType(processedFile.getContentType());
            re.setStoreType(fileVO.getStoreType());
            re.setOriginalName(fileVO.getOriginalName());
            re.setUserId(PoetryUtil.getUserId());
            resourceService.save(re);
            
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
    public PoetryResult<Object> uploadImageWithCompress(
            @RequestParam("file") MultipartFile file, 
            FileVO fileVO,
            @RequestParam(value = "maxWidth", defaultValue = "1920") int maxWidth,
            @RequestParam(value = "maxHeight", defaultValue = "1080") int maxHeight,
            @RequestParam(value = "quality", defaultValue = "0.85") float quality,
            @RequestParam(value = "targetSize", defaultValue = "512000") long targetSize) {
        
        if (file == null || !StringUtils.hasText(fileVO.getType()) || !StringUtils.hasText(fileVO.getRelativePath())) {
            return PoetryResult.fail("文件和资源类型和资源路径不能为空！");
        }

        if (!isImageFile(file.getContentType())) {
            return PoetryResult.fail("请上传图片文件！");
        }

        try {
            log.info("开始智能压缩上传: {}, 原始大小: {}KB, 目标大小: {}KB", 
                    file.getOriginalFilename(), file.getSize() / 1024, targetSize / 1024);
            
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

            fileVO.setFile(compressedFile);
            StoreService storeService = fileStorageService.getFileStorage(fileVO.getStoreType());
            FileVO result = storeService.saveFile(fileVO);

            Resource re = new Resource();
            re.setPath(result.getVisitPath());
            re.setType(fileVO.getType());
            re.setSize(Integer.valueOf(Long.toString(compressedFile.getSize())));
            re.setMimeType(compressedFile.getContentType());
            re.setStoreType(fileVO.getStoreType());
            re.setOriginalName(fileVO.getOriginalName());
            re.setUserId(PoetryUtil.getUserId());
            resourceService.save(re);

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
     * 判断是否为图片文件
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
     * 删除
     */
    @PostMapping("/deleteResource")
    @LoginCheck(0)
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
        resourceService.lambdaQuery()
                .eq(StringUtils.hasText(baseRequestVO.getResourceType()), Resource::getType, baseRequestVO.getResourceType())
                .orderByDesc(Resource::getCreateTime).page(page);
        baseRequestVO.setRecords(page.getRecords());
        baseRequestVO.setTotal(page.getTotal());
        return PoetryResult.success(baseRequestVO);
    }

    /**
     * 修改资源状态
     */
    @GetMapping("/changeResourceStatus")
    @LoginCheck(0)
    public PoetryResult changeResourceStatus(@RequestParam("id") Integer id, @RequestParam("flag") Boolean flag) {
        resourceService.lambdaUpdate().eq(Resource::getId, id).set(Resource::getStatus, flag).update();
        return PoetryResult.success();
    }

    /**
     * 自定义MultipartFile实现，用于压缩后的文件数据
     */
    private static class CompressedMultipartFile implements MultipartFile {
        private final String name;
        private final String originalFilename;
        private final String contentType;
        private final byte[] content;

        public CompressedMultipartFile(String name, String originalFilename, String contentType, byte[] content) {
            this.name = name;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.content = content;
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

