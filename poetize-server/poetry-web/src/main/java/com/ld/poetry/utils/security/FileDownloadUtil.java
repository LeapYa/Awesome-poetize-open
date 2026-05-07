package com.ld.poetry.utils.security;

import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

public final class FileDownloadUtil {

    private static final Set<String> FORCE_DOWNLOAD_EXTENSIONS = Set.of(
            "exe", "dll", "bat", "cmd", "com", "msi",
            "jar", "class",
            "scr", "vbs", "vbe", "js", "jse", "wsf", "wsh", "ps1",
            "sh", "bash", "zsh",
            "py", "pyc", "pyw", "pl", "perl", "rb",
            "go", "rs", "rust", "c", "cpp", "cc", "cxx",
            "php", "php3", "php4", "php5", "phtml", "phar",
            "jsp", "jspx", "jsf", "jspa", "jhtml",
            "asp", "aspx", "asa", "asax", "ascx", "ashx", "asmx",
            "docm", "xlsm", "pptm"
    );

    private FileDownloadUtil() {
    }

    public static boolean shouldForceDownload(String value) {
        String extension = getExtension(value);
        return StringUtils.hasText(extension) && FORCE_DOWNLOAD_EXTENSIONS.contains(extension);
    }

    public static boolean hasUnsafeDownloadPath(String path) {
        if (!StringUtils.hasText(path)) {
            return true;
        }
        String normalized = path.replace('\\', '/');
        String lower = normalized.toLowerCase(Locale.ROOT);
        return normalized.contains("..")
                || normalized.contains("\r")
                || normalized.contains("\n")
                || lower.startsWith("file:")
                || lower.startsWith("jar:");
    }

    public static String sanitizeFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "";
        }
        return fileName.replace("\\", "_")
                .replace("/", "_")
                .replace("\r", "")
                .replace("\n", "")
                .replace("\"", "")
                .trim();
    }

    public static String fileNameFromPath(String path) {
        if (!StringUtils.hasText(path)) {
            return "download";
        }
        String cleanPath = path.split("\\?", 2)[0].replace('\\', '/');
        int slashIndex = cleanPath.lastIndexOf('/');
        String fileName = slashIndex >= 0 ? cleanPath.substring(slashIndex + 1) : cleanPath;
        String safeFileName = sanitizeFileName(fileName);
        return StringUtils.hasText(safeFileName) ? safeFileName : "download";
    }

    public static String contentDispositionAttachment(String fileName) {
        String safeFileName = sanitizeFileName(fileName);
        if (!StringUtils.hasText(safeFileName)) {
            safeFileName = "download";
        }
        String encoded = URLEncoder.encode(safeFileName, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + safeFileName + "\"; filename*=UTF-8''" + encoded;
    }

    private static String getExtension(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String cleanValue = value.split("\\?", 2)[0];
        int slashIndex = Math.max(cleanValue.lastIndexOf('/'), cleanValue.lastIndexOf('\\'));
        int dotIndex = cleanValue.lastIndexOf('.');
        if (dotIndex <= slashIndex || dotIndex == cleanValue.length() - 1) {
            return "";
        }
        return cleanValue.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }
}
