package com.ld.poetry.utils.storage;

import com.ld.poetry.vo.FileVO;
import com.qiniu.common.QiniuException;
import com.qiniu.http.Response;
import com.qiniu.storage.BucketManager;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.storage.UploadManager;
import com.qiniu.storage.model.BatchStatus;
import com.qiniu.storage.model.DefaultPutRet;
import com.qiniu.storage.model.FileInfo;
import com.qiniu.util.Auth;
import com.qiniu.util.StringMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@ConditionalOnProperty(name = "qiniu.enable", havingValue = "true")
public class QiniuUtil implements StoreService {

    /**
     * 七牛云
     */
    @Value("${qiniu.accessKey}")
    private String accessKey;

    @Value("${qiniu.secretKey}")
    private String secretKey;

    @Value("${qiniu.bucket}")
    private String bucket;

    @Value("${qiniu.downloadUrl}")
    private String downloadUrl;

    private static final long EXPIRE_SECONDS = 60L;
    private static final long F_SIZE_LIMIT = 20971520L;

    @Autowired
    private TrustedRemoteStorageReader trustedRemoteStorageReader;

    public String getToken(String key) {
        return getToken(key, false);
    }

    private String getToken(String key, boolean createOnly) {
        StringMap putPolicy = new StringMap();
        putPolicy.put("fsizeLimit", F_SIZE_LIMIT);
        if (createOnly) {
            putPolicy.put("insertOnly", 1);
        }
        Auth auth = Auth.create(accessKey, secretKey);
        return auth.uploadToken(bucket, key, EXPIRE_SECONDS, putPolicy);
    }

    @Override
    public List<StorageDeleteResult> deleteFiles(List<StorageResourceRef> resources) {
        if (CollectionUtils.isEmpty(resources)) {
            return List.of();
        }

        List<StorageDeleteResult> results = new ArrayList<>();
        Configuration cfg = new Configuration(Region.region0());
        Auth auth = Auth.create(accessKey, secretKey);
        BucketManager bucketManager = new BucketManager(auth, cfg);

        for (int offset = 0; offset < resources.size(); offset += 1000) {
            List<StorageResourceRef> batch = resources.subList(offset, Math.min(offset + 1000, resources.size()));
            String[] keys = batch.stream().map(this::resolveKey).toArray(String[]::new);
            try {
                BucketManager.BatchOperations operations = new BucketManager.BatchOperations();
                operations.addDeleteOp(bucket, keys);
                Response response = bucketManager.batch(operations);
                BatchStatus[] statuses = response.jsonToObject(BatchStatus[].class);
                for (int i = 0; i < batch.size(); i++) {
                    StorageResourceRef resource = batch.get(i);
                    BatchStatus status = statuses[i];
                    if (status.code == 200) {
                        results.add(StorageDeleteResult.deleted(resource));
                    } else if (status.code == 612) {
                        results.add(StorageDeleteResult.missing(resource));
                    } else {
                        String message = status.data == null ? "七牛云删除失败" : status.data.error;
                        results.add(StorageDeleteResult.failed(resource, message));
                    }
                }
            } catch (Exception e) {
                log.error("七牛云批量删除失败", e);
                batch.forEach(resource -> results.add(StorageDeleteResult.failed(resource, e.getMessage())));
            }
        }
        return results;
    }

    @Override
    public FileVO saveFile(FileVO fileVO) {
        if (fileVO == null || fileVO.getFile() == null || fileVO.getFile().isEmpty()) {
            throw new IllegalArgumentException("文件参数不能为空");
        }
        if (fileVO.getFile().getSize() > F_SIZE_LIMIT) {
            throw new IllegalArgumentException("七牛云单文件不能超过20MB");
        }

        String key = normalizeKey(fileVO.getRelativePath());
        Configuration cfg = new Configuration(Region.region0());
        UploadManager uploadManager = new UploadManager(cfg);
        try (InputStream inputStream = fileVO.getFile().getInputStream()) {
            Response response = uploadManager.put(
                    inputStream,
                    key,
                    getToken(key, Boolean.TRUE.equals(fileVO.getCreateOnly())),
                    null,
                    fileVO.getFile().getContentType()
            );
            DefaultPutRet putRet = response.jsonToObject(DefaultPutRet.class);
            fileVO.setStoreType(StoreEnum.QINIU.getCode());
            fileVO.setStorageKey(key);
            fileVO.setVisitPath(joinDownloadUrl(key));
            if (putRet != null && putRet.key != null) {
                fileVO.setStorageKey(putRet.key);
                fileVO.setVisitPath(joinDownloadUrl(putRet.key));
            }
            return fileVO;
        } catch (Exception e) {
            log.error("七牛云上传失败: key={}", key, e);
            throw new IllegalStateException("七牛云上传失败: " + e.getMessage(), e);
        }
    }

    @Override
    public StorageReadHandle openRead(StorageResourceRef resource, long maxBytes) {
        String objectUrl = resolveObjectUrl(resource);
        String signedUrl = Auth.create(accessKey, secretKey).privateDownloadUrl(objectUrl, EXPIRE_SECONDS);
        return trustedRemoteStorageReader.open(
                signedUrl,
                trustedRemoteStorageReader.parseTrustedHosts(downloadUrl),
                maxBytes
        );
    }

    @Override
    public StorageRangeReadHandle openReadRange(StorageResourceRef resource,
                                                long startInclusive,
                                                long endInclusive) {
        String objectUrl = resolveObjectUrl(resource);
        String signedUrl = Auth.create(accessKey, secretKey).privateDownloadUrl(objectUrl, EXPIRE_SECONDS);
        return trustedRemoteStorageReader.openRange(
                signedUrl,
                trustedRemoteStorageReader.parseTrustedHosts(downloadUrl),
                startInclusive,
                endInclusive
        );
    }

    @Override
    public StorageClientAccess resolveClientAccess(StorageResourceRef resource) {
        String objectUrl = resolveObjectUrl(resource);
        String signedUrl = Auth.create(accessKey, secretKey).privateDownloadUrl(objectUrl, EXPIRE_SECONDS);
        return new StorageClientAccess(signedUrl, 0, true);
    }

    @Override
    public StorageVerificationResult verify(StorageResourceRef resource) {
        String key;
        try {
            key = resolveKey(resource);
        } catch (IllegalArgumentException e) {
            return StorageVerificationResult.unknown("当前七牛访问地址无法确定性解析对象键");
        }
        try {
            Configuration cfg = new Configuration(Region.region0());
            BucketManager bucketManager = new BucketManager(Auth.create(accessKey, secretKey), cfg);
            FileInfo fileInfo = bucketManager.stat(bucket, key);
            return StorageVerificationResult.available(fileInfo.fsize, null);
        } catch (QiniuException e) {
            if (e.code() == 612) {
                return StorageVerificationResult.missing("七牛云对象不存在");
            }
            return StorageVerificationResult.unknown(e.getMessage());
        }
    }

    @Override
    public StorageCapability getCapability() {
        boolean readable = !trustedRemoteStorageReader.parseTrustedHosts(downloadUrl).isEmpty();
        return new StorageCapability(
                StoreEnum.QINIU.getCode(), true, readable, true, true, true,
                F_SIZE_LIMIT, List.of()
        );
    }

    @Override
    public String resolveAccessPath(String storageKey) {
        return org.springframework.util.StringUtils.hasText(storageKey)
                ? joinDownloadUrl(normalizeKey(storageKey))
                : null;
    }

    @Override
    public String resolveStorageKey(String accessPath) {
        if (!isPublicAccessPathTrusted(accessPath)) {
            return null;
        }
        try {
            java.net.URI uri = java.net.URI.create(accessPath);
            if (StringUtils.hasText(uri.getRawQuery())
                    || StringUtils.hasText(uri.getRawFragment())
                    || (uri.getRawPath() != null && uri.getRawPath().contains("%"))) {
                return null;
            }
            java.net.URI base = java.net.URI.create(downloadUrl.endsWith("/") ? downloadUrl : downloadUrl + "/");
            String basePath = base.getPath();
            String path = uri.getPath();
            if (!StringUtils.hasText(path) || !path.startsWith(basePath)) {
                return null;
            }
            String key = path.substring(basePath.length());
            return StringUtils.hasText(key) ? normalizeKey(key) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public boolean supportsDeterministicWrite() {
        return true;
    }

    @Override
    public boolean isPublicAccessPathTrusted(String accessPath) {
        return trustedRemoteStorageReader.isTrustedPublicUrl(
                accessPath,
                trustedRemoteStorageReader.parseTrustedHosts(downloadUrl)
        );
    }

    private String resolveObjectUrl(StorageResourceRef resource) {
        if (resource != null && org.springframework.util.StringUtils.hasText(resource.storageKey())) {
            return joinDownloadUrl(normalizeKey(resource.storageKey()));
        }
        String accessPath = resource == null ? null : resource.path();
        if (!isPublicAccessPathTrusted(accessPath)) {
            throw new IllegalArgumentException("七牛云访问地址不属于当前配置的可信域名");
        }
        return accessPath;
    }

    private String resolveKey(StorageResourceRef resource) {
        if (resource != null && org.springframework.util.StringUtils.hasText(resource.storageKey())) {
            return normalizeKey(resource.storageKey());
        }
        String resolved = resolveStorageKey(resource == null ? null : resource.path());
        if (!org.springframework.util.StringUtils.hasText(resolved)) {
            throw new IllegalArgumentException("当前七牛访问地址无法确定性解析对象键");
        }
        return resolved;
    }

    private String normalizeKey(String value) {
        if (!org.springframework.util.StringUtils.hasText(value)) {
            throw new IllegalArgumentException("七牛云对象键不能为空");
        }
        String key = value.trim().replace('\\', '/');
        if (key.startsWith(downloadUrl)) {
            key = key.substring(downloadUrl.length());
        }
        while (key.startsWith("/")) {
            key = key.substring(1);
        }
        if (key.contains("../") || key.equals("..")) {
            throw new IllegalArgumentException("七牛云对象键不合法");
        }
        return key;
    }

    private String joinDownloadUrl(String key) {
        return downloadUrl.endsWith("/") ? downloadUrl + key : downloadUrl + "/" + key;
    }

    @Override
    public String getStoreName() {
        return StoreEnum.QINIU.getCode();
    }

    public Map<String, Map<String, String>> getFileInfo(List<String> files) {
        Map<String, Map<String, String>> result = new HashMap<>();

        //构造一个带指定 Region 对象的配置类
        Configuration cfg = new Configuration(Region.region0());
        Auth auth = Auth.create(accessKey, secretKey);
        BucketManager bucketManager = new BucketManager(auth, cfg);
        try {
            //单次批量请求的文件数量不得超过1000
            String[] keyList = files.toArray(new String[0]);
            BucketManager.BatchOperations batchOperations = new BucketManager.BatchOperations();
            batchOperations.addStatOps(bucket, keyList);
            Response response = bucketManager.batch(batchOperations);
            BatchStatus[] batchStatusList = response.jsonToObject(BatchStatus[].class);
            for (int i = 0; i < keyList.length; i++) {
                BatchStatus status = batchStatusList[i];
                String key = keyList[i];
                if (status.code == 200) {
                    //文件存在
                    Map<String, String> info = new HashMap<>();
                    info.put("size", String.valueOf(status.data.fsize));
                    info.put("mimeType", status.data.mimeType);
                    result.put(key, info);
                } else {
                    log.error(key + "：" + status.data.error);
                }
            }
        } catch (QiniuException ex) {
            log.error(ex.response.toString());
        }

        return result;
    }
}
