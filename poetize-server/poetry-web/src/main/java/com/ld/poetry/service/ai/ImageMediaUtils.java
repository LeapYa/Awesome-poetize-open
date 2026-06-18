package com.ld.poetry.service.ai;

import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * 图片媒体处理工具：MIME 类型推断 + SSRF 防护。
 * <p>
 * 供 {@link com.ld.poetry.service.ai.tools.VisionTools} 和 {@link AiChatService} 共享。
 */
public final class ImageMediaUtils {

    private ImageMediaUtils() {
    }

    /**
     * 根据图片URL推断MIME类型
     */
    public static MimeType resolveMimeType(String imageUrl) {
        if (imageUrl == null) {
            return MimeTypeUtils.IMAGE_JPEG;
        }
        // 去掉查询参数后再判断后缀
        String path = imageUrl;
        int queryIdx = path.indexOf('?');
        if (queryIdx >= 0) {
            path = path.substring(0, queryIdx);
        }
        String lower = path.toLowerCase();
        if (lower.endsWith(".png")) {
            return MimeTypeUtils.IMAGE_PNG;
        } else if (lower.endsWith(".gif")) {
            return MimeTypeUtils.IMAGE_GIF;
        } else if (lower.endsWith(".webp")) {
            return MimeTypeUtils.parseMimeType("image/webp");
        } else if (lower.endsWith(".bmp")) {
            return MimeTypeUtils.parseMimeType("image/bmp");
        }
        return MimeTypeUtils.IMAGE_JPEG;
    }

    // ========== SSRF 防护 ==========

    private static final Pattern PRIVATE_IP_PATTERN = Pattern.compile(
            "(127\\..*)" +                    // 127.0.0.0/8
                    "|(10\\..*)" +                    // 10.0.0.0/8
                    "|(192\\.168\\..*)" +              // 192.168.0.0/16
                    "|(169\\.254\\..*)" +              // 169.254.0.0/16 (link-local)
                    "|(172\\.(1[6-9]|2[0-9]|3[01])\\..*)" +  // 172.16.0.0/12
                    "|(0\\..*)" +                      // 0.0.0.0/8
                    "|(100\\.(6[4-9]|[7-9]\\d|1[01]\\d|12[0-7])\\..*)" +  // 100.64.0.0/10
                    "|(::1?)" +                        // ::1 loopback
                    "|(fc00::.*)" +                    // IPv6 ULA fc00::/7
                    "|(fe80::.*)" +                    // IPv6 link-local
                    "|(fd.*)");                        // IPv6 ULA fd::/8

    /**
     * 校验图片 URL 是否安全（防止 SSRF）。
     * <p>
     * 规则：
     * <ul>
     *   <li>data URI（base64 内联图片）允许 — 不发起网络请求，天然无 SSRF 风险</li>
     *   <li>相对路径（以 / 开头）允许 — 来自本系统上传</li>
     *   <li>仅允许 http/https 协议</li>
     *   <li>拒绝解析到内网/私有 IP 的主机名</li>
     *   <li>拒绝 localhost 及 .local 域名</li>
     * </ul>
     *
     * @return true 表示安全可用
     */
    public static boolean isAllowedImageUrl(String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) {
            return false;
        }

        // data URI（base64 内联）— 不发起网络请求，直接放行
        // 格式：data:image/png;base64,xxxx
        if (imageUrl.startsWith("data:")) {
            return isValidDataUri(imageUrl);
        }

        URI uri;
        try {
            uri = URI.create(imageUrl);
        } catch (IllegalArgumentException e) {
            return false;
        }

        String scheme = uri.getScheme();
        // 相对路径（无 scheme）允许 — 来自本系统上传
        if (scheme == null || scheme.isEmpty()) {
            return true;
        }

        // 仅允许 http/https
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return false;
        }

        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            return false;
        }

        // 拒绝 localhost 及 .local
        String lowerHost = host.toLowerCase();
        if ("localhost".equals(lowerHost) || lowerHost.endsWith(".local")) {
            return false;
        }

        // 拒绝直接以 IP 形式出现的内网地址
        // 使用 matches() 全串匹配，避免 find() 误匹配子串（如 110.0.0.1 中的 10.）
        if (isPrivateIpLiteral(lowerHost)) {
            return false;
        }

        // 解析域名，拒绝解析到内网 IP 的情况
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                if (addr.isLoopbackAddress() || addr.isAnyLocalAddress()
                        || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()) {
                    return false;
                }
            }
        } catch (UnknownHostException e) {
            // 域名无法解析，交由调用方处理（不在此拦截）
        }

        return true;
    }

    /**
     * 判断主机名是否为私有/内网 IP 字面量。
     * 使用 {@code matches()} 全串匹配，避免 {@code find()} 误匹配子串
     * （例如 {@code 110.0.0.1} 中包含 {@code 10.} 子串会被误判）。
     */
    private static boolean isPrivateIpLiteral(String lowerHost) {
        return PRIVATE_IP_PATTERN.matcher(lowerHost).matches();
    }

    /**
     * 校验 data URI 是否为合法的图片 base64 编码。
     * 格式：data:image/{type};base64,{data}
     * 同时校验 base64 数据大小不超过 8MB（约 6MB 原始图片）。
     */
    private static boolean isValidDataUri(String dataUri) {
        // data:image/png;base64,xxxx
        int commaIdx = dataUri.indexOf(',');
        if (commaIdx < 0 || commaIdx >= dataUri.length() - 1) {
            return false;
        }
        String meta = dataUri.substring(0, commaIdx);
        // 必须是 image/* 类型
        if (!meta.startsWith("data:image/")) {
            return false;
        }
        // 必须是 base64 编码
        if (!meta.contains(";base64")) {
            return false;
        }
        // 校验大小：base64 字符串长度不超过 8MB（约 6MB 原始数据）
        // 8MB base64 ≈ 6MB 原始图片，配合前端 5MB 单张限制留有余量
        long base64Length = (long) dataUri.length() - (commaIdx + 1);
        if (base64Length > 8L * 1024 * 1024) {
            return false;
        }
        return true;
    }
}
