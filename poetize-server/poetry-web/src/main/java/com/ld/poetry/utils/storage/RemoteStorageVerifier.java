package com.ld.poetry.utils.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.OptionalLong;
import java.util.Set;

@Component
public class RemoteStorageVerifier {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_REDIRECTS = 5;

    @Value("${resource.migration.remote.allow-private-hosts:false}")
    private boolean allowPrivateHosts;

    private final TrustedRemoteStorageReader trustedRemoteStorageReader;

    public RemoteStorageVerifier(TrustedRemoteStorageReader trustedRemoteStorageReader) {
        this.trustedRemoteStorageReader = trustedRemoteStorageReader;
    }

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public StorageVerificationResult verify(StorageResourceRef resource, Set<String> trustedOrigins) {
        if (resource == null || !StringUtils.hasText(resource.path())) {
            return StorageVerificationResult.unknown("远端资源URL不能为空");
        }
        if (trustedOrigins == null || trustedOrigins.isEmpty()) {
            return StorageVerificationResult.unknown("当前存储平台未配置可信下载地址");
        }
        try {
            URI uri = URI.create(resource.path());
            return verifyWithHead(uri, trustedOrigins, 0);
        } catch (IllegalArgumentException e) {
            return StorageVerificationResult.unknown(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return StorageVerificationResult.unknown("远端校验被中断");
        } catch (Exception e) {
            return StorageVerificationResult.unknown(e.getMessage());
        }
    }

    private StorageVerificationResult verifyWithHead(URI uri,
                                                     Set<String> trustedOrigins,
                                                     int redirectCount) throws Exception {
        if (redirectCount > MAX_REDIRECTS) {
            return StorageVerificationResult.unknown("远端资源重定向次数过多");
        }
        validateUri(uri, trustedOrigins);
        HttpRequest head = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .header("User-Agent", "Poetize-Resource-Migration/1.0")
                .build();
        HttpResponse<Void> response = httpClient.send(head, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() >= 300 && response.statusCode() < 400) {
            String location = response.headers().firstValue("Location").orElse(null);
            if (!StringUtils.hasText(location)) {
                return StorageVerificationResult.unknown("远端重定向缺少Location");
            }
            return verifyWithHead(uri.resolve(location), trustedOrigins, redirectCount + 1);
        }
        if (response.statusCode() == 404 || response.statusCode() == 410) {
            return StorageVerificationResult.missing("远端资源不存在");
        }
        if (response.statusCode() >= 200 && response.statusCode() < 400) {
            OptionalLong size = response.headers().firstValueAsLong("Content-Length");
            return StorageVerificationResult.available(size.isPresent() ? size.getAsLong() : null, null);
        }
        if (response.statusCode() != 405 && response.statusCode() != 403) {
            return StorageVerificationResult.unknown("远端校验返回HTTP " + response.statusCode());
        }

        return verifyWithRange(uri, trustedOrigins, 0);
    }

    private StorageVerificationResult verifyWithRange(URI uri,
                                                      Set<String> trustedOrigins,
                                                      int redirectCount) throws Exception {
        if (redirectCount > MAX_REDIRECTS) {
            return StorageVerificationResult.unknown("远端资源重定向次数过多");
        }
        validateUri(uri, trustedOrigins);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .header("Range", "bytes=0-0")
                .header("User-Agent", "Poetize-Resource-Migration/1.0")
                .build();
        HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() >= 300 && response.statusCode() < 400) {
            String location = response.headers().firstValue("Location").orElse(null);
            if (!StringUtils.hasText(location)) {
                return StorageVerificationResult.unknown("远端重定向缺少Location");
            }
            return verifyWithRange(uri.resolve(location), trustedOrigins, redirectCount + 1);
        }
        if (response.statusCode() == 404 || response.statusCode() == 410) {
            return StorageVerificationResult.missing("远端资源不存在");
        }
        if (response.statusCode() != 200 && response.statusCode() != 206) {
            return StorageVerificationResult.unknown("远端校验返回HTTP " + response.statusCode());
        }

        Long totalSize = parseContentRangeSize(response.headers().firstValue("Content-Range").orElse(null));
        if (totalSize == null && response.statusCode() == 200) {
            OptionalLong contentLength = response.headers().firstValueAsLong("Content-Length");
            totalSize = contentLength.isPresent() ? contentLength.getAsLong() : null;
        }
        return StorageVerificationResult.available(totalSize, null);
    }

    private void validateUri(URI uri, Set<String> trustedOrigins) {
        String scheme = uri == null ? null : uri.getScheme();
        String host = uri == null ? null : uri.getHost();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                || !StringUtils.hasText(host)
                || uri.getUserInfo() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException("远端资源URL不合法");
        }
        if (!trustedRemoteStorageReader.isTrustedPublicUrl(uri.toString(), trustedOrigins)) {
            throw new IllegalArgumentException("远端资源地址不在当前存储平台白名单内");
        }
        if (!allowPrivateHosts) {
            trustedRemoteStorageReader.rejectPrivateAddresses(host);
        }
    }

    private Long parseContentRangeSize(String contentRange) {
        if (!StringUtils.hasText(contentRange)) {
            return null;
        }
        int slashIndex = contentRange.lastIndexOf('/');
        if (slashIndex < 0 || slashIndex == contentRange.length() - 1) {
            return null;
        }
        String total = contentRange.substring(slashIndex + 1).trim();
        if ("*".equals(total)) {
            return null;
        }
        try {
            return Long.parseLong(total);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
