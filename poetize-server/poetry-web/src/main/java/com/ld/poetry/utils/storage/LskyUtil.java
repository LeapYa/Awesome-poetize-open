package com.ld.poetry.utils.storage;

import com.ld.poetry.handle.PoetryRuntimeException;
import com.ld.poetry.service.SysConfigService;
import com.ld.poetry.vo.FileVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 兰空图床存储服务
 */
@Slf4j
@Component
public class LskyUtil implements StoreService {

    @Autowired
    private SysConfigService sysConfigService;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private RemoteStorageVerifier remoteStorageVerifier;

    @Autowired
    private TrustedRemoteStorageReader trustedRemoteStorageReader;

    private String getUrl() {
        return sysConfigService.getConfigValueByKey("lsky.url");
    }

    private String getToken() {
        return sysConfigService.getConfigValueByKey("lsky.token");
    }

    private String getStrategyId() {
        return sysConfigService.getConfigValueByKey("lsky.strategy_id");
    }

    private String getDownloadHosts() {
        return sysConfigService.getConfigValueByKey("lsky.download_hosts");
    }

    private boolean isEnabled() {
        String enable = sysConfigService.getConfigValueByKey("lsky.enable");
        return "true".equalsIgnoreCase(enable);
    }

    @Override
    public List<StorageDeleteResult> deleteFiles(List<StorageResourceRef> resources) {
        if (!isEnabled()) {
            return resources == null ? List.of() : resources.stream()
                    .map(resource -> StorageDeleteResult.failed(resource, "兰空图床未启用"))
                    .toList();
        }
        if (CollectionUtils.isEmpty(resources)) {
            return List.of();
        }

        return resources.stream().map(resource -> {
            String key = StringUtils.hasText(resource.storageKey())
                    ? resource.storageKey()
                    : resource.originalName();
            if (!StringUtils.hasText(key)) {
                return StorageDeleteResult.failed(resource, "缺少兰空图床图片 key");
            }

            try {
                HttpHeaders headers = new HttpHeaders();
                headers.set("Authorization", "Bearer " + getToken());
                headers.set("Accept", "application/json");
                HttpEntity<String> entity = new HttpEntity<>(headers);
                ResponseEntity<Map> response = restTemplate.exchange(
                        getUrl() + "/images/" + key,
                        HttpMethod.DELETE,
                        entity,
                        Map.class
                );
                Map<String, Object> responseData = response.getBody();
                if (response.getStatusCode().is2xxSuccessful()
                        && responseData != null
                        && Boolean.TRUE.equals(responseData.get("status"))) {
                    return StorageDeleteResult.deleted(resource);
                }
                String message = responseData == null
                        ? "兰空图床返回空响应"
                        : String.valueOf(responseData.get("message"));
                return StorageDeleteResult.failed(resource, message);
            } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
                return StorageDeleteResult.missing(resource);
            } catch (Exception e) {
                log.error("兰空图床文件删除异常：{}", resource.path(), e);
                return StorageDeleteResult.failed(resource, e.getMessage());
            }
        }).toList();
    }

    @Override
    public FileVO saveFile(FileVO fileVO) {
        if (!isEnabled()) {
            throw new PoetryRuntimeException("兰空图床未启用，无法上传文件");
        }

        try {
            MultipartFile file = fileVO.getFile();
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set("Authorization", "Bearer " + getToken());
            headers.set("Accept", "application/json");
            
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

            // 添加文件（流式上传，避免大图全量读入内存）
            body.add("file", file.getResource());
            
            // 添加存储策略ID（如果有）
            String strategyId = getStrategyId();
            if (StringUtils.hasText(strategyId)) {
                body.add("strategy_id", strategyId);
            }
            
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            
            // 上传图片
            ResponseEntity<Map> response = restTemplate.exchange(
                    getUrl() + "/upload",
                    HttpMethod.POST,
                    requestEntity,
                    Map.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                
                if (Boolean.TRUE.equals(responseBody.get("status"))) {
                    Map<String, Object> responseData = (Map<String, Object>) responseBody.get("data");
                    if (responseData != null) {
                        // 获取图片URL和其他信息
                        String key = (String) responseData.get("key");
                        Map<String, Object> links = (Map<String, Object>) responseData.get("links");
                        String imageUrl = (String) links.get("url");
                        
                        fileVO.setAbsolutePath(imageUrl);
                        fileVO.setVisitPath(imageUrl);
                        fileVO.setStoreType(StoreEnum.LSKY.getCode());
                        fileVO.setStorageKey(key);
                        return fileVO;
                    }
                } else {
                    throw new PoetryRuntimeException("兰空图床上传失败：" + responseBody.get("message"));
                }
            }
            
            throw new PoetryRuntimeException("兰空图床上传失败，请检查网络或API配置");
        } catch (Exception e) {
            log.error("兰空图床上传异常", e);
            throw new PoetryRuntimeException("兰空图床上传异常：" + e.getMessage());
        }
    }

    @Override
    public StorageReadHandle openRead(StorageResourceRef resource, long maxBytes) {
        if (!isEnabled()) {
            throw new IllegalStateException("兰空图床未启用");
        }
        return trustedRemoteStorageReader.open(
                resource.path(),
                trustedRemoteStorageReader.parseTrustedHosts(getUrl(), getDownloadHosts()),
                maxBytes
        );
    }

    @Override
    public StorageRangeReadHandle openReadRange(StorageResourceRef resource,
                                                long startInclusive,
                                                long endInclusive) {
        if (!isEnabled()) {
            throw new IllegalStateException("兰空图床未启用");
        }
        return trustedRemoteStorageReader.openRange(
                resource.path(),
                trustedRemoteStorageReader.parseTrustedHosts(getUrl(), getDownloadHosts()),
                startInclusive,
                endInclusive
        );
    }

    @Override
    public StorageClientAccess resolveClientAccess(StorageResourceRef resource) {
        if (!isEnabled() || !isPublicAccessPathTrusted(resource.path())) {
            return null;
        }
        return new StorageClientAccess(resource.path(), 60, false);
    }

    @Override
    public StorageVerificationResult verify(StorageResourceRef resource) {
        return remoteStorageVerifier.verify(
                resource,
                trustedRemoteStorageReader.parseTrustedHosts(getUrl(), getDownloadHosts())
        );
    }

    @Override
    public StorageCapability getCapability() {
        boolean enabled = isEnabled();
        boolean readable = enabled
                && !trustedRemoteStorageReader.parseTrustedHosts(getUrl(), getDownloadHosts()).isEmpty();
        return new StorageCapability(
                StoreEnum.LSKY.getCode(), enabled, readable, true, true, true,
                0, List.of("image/")
        );
    }

    @Override
    public boolean isPublicAccessPathTrusted(String accessPath) {
        return trustedRemoteStorageReader.isTrustedPublicUrl(
                accessPath,
                trustedRemoteStorageReader.parseTrustedHosts(getUrl(), getDownloadHosts())
        );
    }

    @Override
    public String getStoreName() {
        return StoreEnum.LSKY.getCode();
    }
} 