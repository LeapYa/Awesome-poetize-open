package com.ld.poetry.utils.storage;

import com.ld.poetry.handle.PoetryRuntimeException;
import com.ld.poetry.vo.FileVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * EasyImage 2.0简单图床存储服务
 */
@Slf4j
@Component
public class EasyImageUtil implements StoreService {
    
    @Value("${easyimage.url:}")
    private String easyImageUrl;
    
    @Value("${easyimage.token:}")
    private String easyImageToken;
    
    @Value("${easyimage.enable:false}")
    private Boolean easyImageEnable;

    @Value("${easyimage.download_hosts:}")
    private String easyImageDownloadHosts;
    
    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private RemoteStorageVerifier remoteStorageVerifier;

    @Autowired
    private TrustedRemoteStorageReader trustedRemoteStorageReader;
    
    @Override
    public FileVO saveFile(FileVO fileVO) {
        // 参数校验
        if (fileVO == null || fileVO.getFile() == null) {
            throw new PoetryRuntimeException("文件参数不能为空！");
        }

        // 检查是否启用EasyImage
        if (easyImageEnable == null || !easyImageEnable || !StringUtils.hasText(easyImageUrl) || !StringUtils.hasText(easyImageToken)) {
            throw new PoetryRuntimeException("简单图床未正确配置！");
        }
        
        try {
            MultipartFile file = fileVO.getFile();
            
            // 构建请求头和表单数据
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            
            MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
            form.add("image", file.getResource());
            form.add("token", easyImageToken);
            
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(form, headers);
            
            // 发送上传请求
            log.info("正在上传文件到简单图床: {}", file.getOriginalFilename());
            EasyImageResponse response = restTemplate.postForObject(easyImageUrl, requestEntity, EasyImageResponse.class);
            
            // 处理响应
            if (response == null) {
                throw new PoetryRuntimeException("简单图床返回空响应");
            }
            
            if (!"success".equals(response.getResult()) || response.getCode() != 200) {
                log.error("EasyImage上传失败: {}", response);
                throw new PoetryRuntimeException("简单图床上传失败: " + response.getResult());
            }
            
            String imageUrl = response.getUrl().replace("\\", "");
            String delUrl = response.getDel();
            if (!StringUtils.hasText(delUrl)) {
                throw new PoetryRuntimeException("简单图床未返回删除凭证，无法安全纳管该副本");
            }
            fileVO.setAbsolutePath(imageUrl);
            fileVO.setVisitPath(imageUrl);
            fileVO.setStoreType(StoreEnum.EASYIMAGE.getCode());
            // EasyImage 的 storageKey 存储删除凭证（del 链接），因为该图床无对象键 API，
            // 读取靠 visitPath（url），删除靠 storageKey（del 链接 GET 请求）。
            fileVO.setStorageKey(delUrl);
            log.info("简单图床上传成功: {}", imageUrl);
            
            return fileVO;
        } catch (Exception e) {
            log.error("简单图床上传出错", e);
            throw new PoetryRuntimeException("简单图床上传出错: " + e.getMessage());
        }
    }

    @Override
    public List<StorageDeleteResult> deleteFiles(List<StorageResourceRef> resources) {
        if (resources == null || resources.isEmpty()) {
            return List.of();
        }
        return resources.stream().map(resource -> {
            String delUrl = resource.storageKey();
            if (!StringUtils.hasText(delUrl) || !delUrl.startsWith("http")) {
                return StorageDeleteResult.failed(resource, "EasyImage 缺少删除凭证（del 链接）");
            }
            if (!trustedRemoteStorageReader.isTrustedPublicUrl(
                    delUrl,
                    trustedRemoteStorageReader.parseTrustedHosts(easyImageUrl, easyImageDownloadHosts)
            )) {
                return StorageDeleteResult.failed(resource, "EasyImage 删除凭证地址不在可信主机白名单内");
            }
            try {
                // EasyImage 删除协议：GET 请求上传时返回的 del 链接
                restTemplate.getForObject(delUrl, String.class);
                return StorageDeleteResult.deleted(resource);
            } catch (HttpClientErrorException.NotFound e) {
                return StorageDeleteResult.missing(resource);
            } catch (Exception e) {
                log.error("EasyImage 文件删除异常：{}", resource.path(), e);
                return StorageDeleteResult.failed(resource, e.getMessage());
            }
        }).toList();
    }

    @Override
    public StorageReadHandle openRead(StorageResourceRef resource, long maxBytes) {
        if (!Boolean.TRUE.equals(easyImageEnable)) {
            throw new IllegalStateException("EasyImage 未启用");
        }
        return trustedRemoteStorageReader.open(
                resource.path(),
                trustedRemoteStorageReader.parseTrustedHosts(easyImageUrl, easyImageDownloadHosts),
                maxBytes
        );
    }

    @Override
    public StorageRangeReadHandle openReadRange(StorageResourceRef resource,
                                                long startInclusive,
                                                long endInclusive) {
        if (!Boolean.TRUE.equals(easyImageEnable)) {
            throw new IllegalStateException("EasyImage 未启用");
        }
        return trustedRemoteStorageReader.openRange(
                resource.path(),
                trustedRemoteStorageReader.parseTrustedHosts(easyImageUrl, easyImageDownloadHosts),
                startInclusive,
                endInclusive
        );
    }

    @Override
    public StorageClientAccess resolveClientAccess(StorageResourceRef resource) {
        if (!Boolean.TRUE.equals(easyImageEnable)
                || !isPublicAccessPathTrusted(resource.path())) {
            return null;
        }
        return new StorageClientAccess(resource.path(), 60, false);
    }

    @Override
    public StorageVerificationResult verify(StorageResourceRef resource) {
        return remoteStorageVerifier.verify(
                resource,
                trustedRemoteStorageReader.parseTrustedHosts(easyImageUrl, easyImageDownloadHosts)
        );
    }

    @Override
    public StorageCapability getCapability() {
        boolean enabled = Boolean.TRUE.equals(easyImageEnable)
                && StringUtils.hasText(easyImageUrl)
                && StringUtils.hasText(easyImageToken);
        boolean readable = enabled
                && !trustedRemoteStorageReader.parseTrustedHosts(easyImageUrl, easyImageDownloadHosts).isEmpty();
        return new StorageCapability(
                StoreEnum.EASYIMAGE.getCode(), enabled, readable, true, true, true,
                0, List.of("image/")
        );
    }

    @Override
    public boolean isPublicAccessPathTrusted(String accessPath) {
        return trustedRemoteStorageReader.isTrustedPublicUrl(
                accessPath,
                trustedRemoteStorageReader.parseTrustedHosts(easyImageUrl, easyImageDownloadHosts)
        );
    }

    @Override
    public String getStoreName() {
        return StoreEnum.EASYIMAGE.getCode();
    }

    /**
     * 简单图床响应结构
     */
    private static class EasyImageResponse {
        private String result;
        private int code;
        private String url;
        private String srcName;
        private String thumb;
        private String del;

        public String getResult() {
            return result;
        }

        public void setResult(String result) {
            this.result = result;
        }

        public int getCode() {
            return code;
        }

        public void setCode(int code) {
            this.code = code;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getSrcName() {
            return srcName;
        }

        public void setSrcName(String srcName) {
            this.srcName = srcName;
        }

        public String getThumb() {
            return thumb;
        }

        public void setThumb(String thumb) {
            this.thumb = thumb;
        }

        public String getDel() {
            return del;
        }

        public void setDel(String del) {
            this.del = del;
        }

        @Override
        public String toString() {
            return "EasyImageResponse{" +
                    "result='" + result + '\'' +
                    ", code=" + code +
                    ", url='" + url + '\'' +
                    ", srcName='" + srcName + '\'' +
                    '}';
        }
    }
} 