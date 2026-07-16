package com.ld.poetry.service;

import com.ld.poetry.utils.mail.MailUtil;
import com.ld.poetry.utils.storage.FileStorageService;
import com.ld.poetry.utils.storage.StorageCapability;
import com.ld.poetry.utils.storage.StorageResourceRef;
import com.ld.poetry.utils.storage.StoreEnum;
import com.ld.poetry.utils.storage.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ResourceAdoptionSourceResolver {

    private final FileStorageService fileStorageService;
    private final MailUtil mailUtil;

    @Value("${local.downloadUrl:/static/}")
    private String localDownloadUrl = "/static/";

    public Inspection inspect(String sourceUrl) {
        try {
            ResolvedSource source = resolve(sourceUrl);
            return new Inspection(true, source.storeType(), "");
        } catch (RuntimeException e) {
            return new Inspection(false, null, safeMessage(e));
        }
    }

    public ResolvedSource resolve(String sourceUrl) {
        String identity = normalizeIdentity(sourceUrl);
        if (identity.startsWith("/") && !identity.startsWith("//")) {
            return resolveLocal(identity, identity);
        }

        URI uri = parseHttpUri(identity);
        if (isConfiguredSiteOrigin(uri) && isLocalStaticPath(uri.getPath())) {
            if (StringUtils.hasText(uri.getRawQuery()) || (uri.getRawPath() != null && uri.getRawPath().contains("%"))) {
                throw new IllegalArgumentException("本站静态URL含查询或编码路径，无法证明其与本地原文件字节等价");
            }
            return resolveLocal(identity, uri.getPath());
        }

        String readUrl = identity.startsWith("//") ? "https:" + identity : identity;
        List<StoreService> matchingStores = fileStorageService.listFileStorages().stream()
                .filter(store -> {
                    StorageCapability capability = store.getCapability();
                    return capability.enabled()
                            && capability.readSupported()
                            && store.isPublicAccessPathTrusted(readUrl);
                })
                .toList();
        if (matchingStores.isEmpty()) {
            throw new IllegalArgumentException("历史资源URL不属于本站或已配置的可信图床域名");
        }
        if (matchingStores.size() > 1) {
            throw new IllegalStateException("历史资源URL同时匹配多个存储平台，必须先消除可信域名配置歧义");
        }

        StoreService store = matchingStores.getFirst();
        String storageKey = store.resolveStorageKey(readUrl);
        return resolved(identity, readUrl, store, storageKey);
    }

    private ResolvedSource resolveLocal(String identity, String localPath) {
        StoreService store = fileStorageService.getFileStorageByStoreType(StoreEnum.LOCAL.getCode());
        StorageCapability capability = store.getCapability();
        if (!capability.enabled() || !capability.readSupported()) {
            throw new IllegalStateException("本地存储当前不支持完整读取");
        }
        String storageKey = store.resolveStorageKey(localPath);
        if (!StringUtils.hasText(storageKey)) {
            throw new IllegalArgumentException("本站历史地址不属于受控本地静态目录");
        }
        String accessPath = store.resolveAccessPath(storageKey);
        if (!StringUtils.hasText(accessPath) || !store.isPublicAccessPathTrusted(accessPath)) {
            throw new IllegalStateException("本地存储无法重建受控物理地址");
        }
        return resolved(identity, accessPath, store, storageKey);
    }

    private ResolvedSource resolved(String identity,
                                    String accessPath,
                                    StoreService store,
                                    String storageKey) {
        String originalName = fileName(accessPath);
        StorageResourceRef ref = new StorageResourceRef(
                null,
                accessPath,
                storageKey,
                originalName,
                null,
                null,
                null
        );
        return new ResolvedSource(
                identity,
                store.getStoreName(),
                storageKey,
                accessPath,
                originalName,
                store,
                ref
        );
    }

    private String normalizeIdentity(String sourceUrl) {
        if (!StringUtils.hasText(sourceUrl)) {
            throw new IllegalArgumentException("历史资源URL不能为空");
        }
        String normalized = sourceUrl.trim();
        int fragment = normalized.indexOf('#');
        if (fragment >= 0) {
            normalized = normalized.substring(0, fragment);
        }
        if (!StringUtils.hasText(normalized)
                || normalized.length() > 2048
                || normalized.contains("\r")
                || normalized.contains("\n")
                || normalized.startsWith("/media/")) {
            throw new IllegalArgumentException("历史资源URL不合法");
        }
        if (normalized.startsWith("/") && !normalized.startsWith("//")) {
            if (!isLocalStaticPath(stripQuery(normalized))) {
                throw new IllegalArgumentException("本站相对地址不属于受控静态目录");
            }
            if (normalized.contains("?") || normalized.contains("%")) {
                throw new IllegalArgumentException("本站静态URL含查询或编码路径，无法证明其与本地原文件字节等价");
            }
            return normalized;
        }
        parseHttpUri(normalized);
        return normalized;
    }

    private URI parseHttpUri(String value) {
        try {
            URI uri = URI.create(value.startsWith("//") ? "https:" + value : value);
            String scheme = uri.getScheme();
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    || !StringUtils.hasText(uri.getHost())
                    || uri.getUserInfo() != null
                    || uri.getFragment() != null) {
                throw new IllegalArgumentException("历史资源URL必须是无凭据的HTTP/HTTPS地址");
            }
            return uri;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("历史资源URL格式不合法", e);
        }
    }

    private boolean isConfiguredSiteOrigin(URI source) {
        String siteUrl = mailUtil.getSiteUrl();
        if (!StringUtils.hasText(siteUrl)) {
            return false;
        }
        try {
            return origin(source).equals(origin(URI.create(siteUrl)));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private String origin(URI uri) {
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        int normalizedPort = port >= 0 ? port : "https".equals(scheme) ? 443 : 80;
        return scheme + "://" + host + ":" + normalizedPort;
    }

    private boolean isLocalStaticPath(String path) {
        if (!StringUtils.hasText(path)) {
            return false;
        }
        String prefix = localPrefix();
        return path.startsWith(prefix) && path.length() > prefix.length();
    }

    private String localPrefix() {
        String value = StringUtils.hasText(localDownloadUrl) ? localDownloadUrl.trim() : "/static/";
        if (value.startsWith("http://") || value.startsWith("https://") || value.startsWith("//")) {
            try {
                URI uri = URI.create(value.startsWith("//") ? "https:" + value : value);
                value = uri.getPath();
            } catch (IllegalArgumentException ignored) {
                value = "/static/";
            }
        }
        if (!StringUtils.hasText(value) || !value.startsWith("/")) {
            value = "/static/";
        }
        return value.endsWith("/") ? value : value + "/";
    }

    private String stripQuery(String value) {
        int query = value.indexOf('?');
        return query >= 0 ? value.substring(0, query) : value;
    }

    private String fileName(String value) {
        try {
            URI uri = URI.create(value.startsWith("//") ? "https:" + value : value);
            String path = uri.getPath();
            int slash = path == null ? -1 : path.lastIndexOf('/');
            String name = slash >= 0 ? path.substring(slash + 1) : path;
            return StringUtils.hasText(name) ? name : "resource";
        } catch (IllegalArgumentException e) {
            return "resource";
        }
    }

    private String safeMessage(RuntimeException e) {
        return StringUtils.hasText(e.getMessage()) ? e.getMessage() : "历史资源来源不可信";
    }

    public record Inspection(boolean trusted, String storeType, String reason) {
    }

    public record ResolvedSource(
            String sourceUrl,
            String storeType,
            String storageKey,
            String accessPath,
            String originalName,
            StoreService storeService,
            StorageResourceRef resourceRef
    ) {
    }
}