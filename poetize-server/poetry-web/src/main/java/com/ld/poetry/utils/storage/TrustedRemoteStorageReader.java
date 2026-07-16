package com.ld.poetry.utils.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TrustedRemoteStorageReader {

    private static final int MAX_REDIRECTS = 5;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final Pattern CONTENT_RANGE_PATTERN = Pattern.compile(
            "bytes\\s+(\\d+)-(\\d+)/(\\d+)",
            Pattern.CASE_INSENSITIVE
    );

    @Value("${resource.migration.remote.allow-private-hosts:false}")
    private boolean allowPrivateHosts;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public StorageReadHandle open(String url, Set<String> trustedOrigins, long maxBytes) {
        if (!StringUtils.hasText(url)) {
            throw new IllegalArgumentException("远端资源URL不能为空");
        }
        if (trustedOrigins == null || trustedOrigins.isEmpty()) {
            throw new IllegalStateException("当前存储平台未配置可信下载地址");
        }
        try {
            return open(URI.create(url), normalizeOrigins(trustedOrigins), maxBytes, 0);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("远端资源读取被中断", e);
        } catch (IOException e) {
            throw new IllegalStateException("远端资源读取失败: " + e.getMessage(), e);
        }
    }

    public StorageRangeReadHandle openRange(String url,
                                            Set<String> trustedOrigins,
                                            long startInclusive,
                                            long endInclusive) {
        if (!StringUtils.hasText(url)) {
            throw new IllegalArgumentException("远端资源URL不能为空");
        }
        if (trustedOrigins == null || trustedOrigins.isEmpty()) {
            throw new IllegalStateException("当前存储平台未配置可信下载地址");
        }
        if (startInclusive < 0 || endInclusive < startInclusive) {
            throw new IllegalArgumentException("远端资源读取区间不合法");
        }
        try {
            return openRange(
                    URI.create(url),
                    normalizeOrigins(trustedOrigins),
                    startInclusive,
                    endInclusive,
                    0
            );
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("远端资源区间读取被中断", e);
        } catch (IOException e) {
            throw new IllegalStateException("远端资源区间读取失败: " + e.getMessage(), e);
        }
    }

    public Set<String> parseTrustedHosts(String... values) {
        Set<String> origins = new LinkedHashSet<>();
        if (values == null) {
            return origins;
        }
        Arrays.stream(values)
                .filter(StringUtils::hasText)
                .flatMap(value -> Arrays.stream(value.split("[,;\\s]+")))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(this::extractOrigin)
                .filter(StringUtils::hasText)
                .forEach(origins::add);
        return Set.copyOf(origins);
    }

    public boolean isTrustedPublicUrl(String url, Set<String> trustedOrigins) {
        if (!StringUtils.hasText(url)
                || url.contains("\r")
                || url.contains("\n")
                || trustedOrigins == null
                || trustedOrigins.isEmpty()) {
            return false;
        }
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && StringUtils.hasText(uri.getHost())
                    && uri.getUserInfo() == null
                    && uri.getFragment() == null
                    && normalizeOrigins(trustedOrigins).contains(originOf(uri));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private StorageReadHandle open(URI uri,
                                   Set<String> trustedOrigins,
                                   long maxBytes,
                                   int redirectCount) throws IOException, InterruptedException {
        if (redirectCount > MAX_REDIRECTS) {
            throw new IllegalStateException("远端资源重定向次数过多");
        }
        validateUri(uri, trustedOrigins);

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .header("Accept-Encoding", "identity")
                .header("User-Agent", "Poetize-Resource-Migration/2.0")
                .build();
        HttpResponse<java.io.InputStream> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofInputStream()
        );

        int status = response.statusCode();
        if (status >= 300 && status < 400) {
            response.body().close();
            String location = response.headers().firstValue("Location")
                    .orElseThrow(() -> new IllegalStateException("远端重定向缺少Location"));
            return open(uri.resolve(location), trustedOrigins, maxBytes, redirectCount + 1);
        }
        if (status == 404 || status == 410) {
            response.body().close();
            throw new IllegalStateException("远端资源不存在");
        }
        if (status < 200 || status >= 300) {
            response.body().close();
            throw new IllegalStateException("远端资源读取返回HTTP " + status);
        }

        String contentEncoding = response.headers().firstValue("Content-Encoding").orElse("");
        if (StringUtils.hasText(contentEncoding) && !"identity".equalsIgnoreCase(contentEncoding)) {
            response.body().close();
            throw new IllegalStateException("远端资源返回了内容编码，无法证明原始字节一致");
        }

        OptionalLong declaredLength = response.headers().firstValueAsLong("Content-Length");
        Long contentLength = declaredLength.isPresent() ? declaredLength.getAsLong() : null;
        String contentType = response.headers().firstValue("Content-Type").orElse(null);
        return StorageReadHandle.bounded(
                response.body(),
                contentLength,
                contentType,
                response.uri(),
                maxBytes
        );
    }

    private StorageRangeReadHandle openRange(URI uri,
                                             Set<String> trustedOrigins,
                                             long startInclusive,
                                             long endInclusive,
                                             int redirectCount) throws IOException, InterruptedException {
        if (redirectCount > MAX_REDIRECTS) {
            throw new IllegalStateException("远端资源重定向次数过多");
        }
        validateUri(uri, trustedOrigins);

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .header("Range", "bytes=" + startInclusive + "-" + endInclusive)
                .header("Accept-Encoding", "identity")
                .header("User-Agent", "Poetize-Stable-Media/1.0")
                .build();
        HttpResponse<java.io.InputStream> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofInputStream()
        );

        int status = response.statusCode();
        if (status >= 300 && status < 400) {
            response.body().close();
            String location = response.headers().firstValue("Location")
                    .orElseThrow(() -> new IllegalStateException("远端重定向缺少Location"));
            return openRange(
                    uri.resolve(location),
                    trustedOrigins,
                    startInclusive,
                    endInclusive,
                    redirectCount + 1
            );
        }
        if (status == 404 || status == 410) {
            response.body().close();
            throw new IllegalStateException("远端资源不存在");
        }
        if (status != 206) {
            response.body().close();
            throw new IllegalStateException("远端资源未按要求返回HTTP 206");
        }

        String contentEncoding = response.headers().firstValue("Content-Encoding").orElse("");
        if (StringUtils.hasText(contentEncoding) && !"identity".equalsIgnoreCase(contentEncoding)) {
            response.body().close();
            throw new IllegalStateException("远端区间响应包含内容编码");
        }

        String contentRange = response.headers().firstValue("Content-Range")
                .orElseThrow(() -> {
                    closeQuietly(response.body());
                    return new IllegalStateException("远端区间响应缺少Content-Range");
                });
        Matcher matcher = CONTENT_RANGE_PATTERN.matcher(contentRange.trim());
        if (!matcher.matches()) {
            response.body().close();
            throw new IllegalStateException("远端区间响应Content-Range不合法");
        }
        long actualStart = Long.parseLong(matcher.group(1));
        long actualEnd = Long.parseLong(matcher.group(2));
        long totalLength = Long.parseLong(matcher.group(3));
        long expectedLength = endInclusive - startInclusive + 1;
        if (actualStart != startInclusive
                || actualEnd != endInclusive
                || totalLength <= actualEnd) {
            response.body().close();
            throw new IllegalStateException("远端区间响应与请求不一致");
        }
        OptionalLong declaredLength = response.headers().firstValueAsLong("Content-Length");
        if (declaredLength.isPresent() && declaredLength.getAsLong() != expectedLength) {
            response.body().close();
            throw new IllegalStateException("远端区间响应长度不一致");
        }
        String contentType = response.headers().firstValue("Content-Type").orElse(null);
        return StorageRangeReadHandle.bounded(
                response.body(),
                expectedLength,
                totalLength,
                contentType,
                response.uri()
        );
    }

    private void closeQuietly(java.io.InputStream inputStream) {
        try {
            inputStream.close();
        } catch (IOException ignored) {
            // 校验失败时尽力释放远端连接。
        }
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
        if (!trustedOrigins.contains(originOf(uri))) {
            throw new IllegalArgumentException("远端资源地址不在当前存储平台白名单内");
        }
        if (!allowPrivateHosts) {
            rejectPrivateAddresses(host);
        }
    }

    private Set<String> normalizeOrigins(Set<String> origins) {
        Set<String> normalized = new LinkedHashSet<>();
        origins.stream()
                .filter(StringUtils::hasText)
                .map(this::extractOrigin)
                .filter(StringUtils::hasText)
                .forEach(normalized::add);
        return Set.copyOf(normalized);
    }

    private String extractOrigin(String value) {
        try {
            URI uri = value.contains("://") ? URI.create(value) : URI.create("https://" + value);
            if (!StringUtils.hasText(uri.getHost())) {
                return null;
            }
            return originOf(uri);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String originOf(URI uri) {
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        boolean defaultPort = port < 0
                || ("http".equals(scheme) && port == 80)
                || ("https".equals(scheme) && port == 443);
        return scheme + "://" + host + (defaultPort ? "" : ":" + port);
    }

    void rejectPrivateAddresses(String host) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) {
                throw new IllegalArgumentException("远端资源域名无法解析");
            }
            for (InetAddress address : addresses) {
                if (isPrivateOrSpecial(address)) {
                    throw new IllegalArgumentException("远端资源域名解析到受保护网络地址");
                }
            }
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("远端资源域名无法解析", e);
        }
    }

    private boolean isPrivateOrSpecial(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = bytes[0] & 0xFF;
            int second = bytes[1] & 0xFF;
            return first == 0
                    || first >= 224
                    || (first == 100 && second >= 64 && second <= 127)
                    || (first == 192 && second == 0)
                    || (first == 198 && (second == 18 || second == 19));
        }
        return (bytes[0] & 0xFE) == 0xFC;
    }
}