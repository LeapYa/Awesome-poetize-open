package com.ld.poetry.controller;

import com.ld.poetry.service.ResourceMediaAccessException;
import com.ld.poetry.service.ResourceMediaService;
import com.ld.poetry.utils.security.FileDownloadUtil;
import com.ld.poetry.utils.storage.StorageClientAccess;
import com.ld.poetry.utils.storage.StorageRangeReadHandle;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

@RestController
@RequiredArgsConstructor
@Slf4j
public class ResourceMediaController {

    private static final String CACHE_CONTROL = "public, max-age=0, must-revalidate";
    private static final String NO_STORE = "private, no-store, max-age=0";

    private final ResourceMediaService resourceMediaService;

    @RequestMapping(value = "/media/{publicId}", method = {RequestMethod.GET, RequestMethod.HEAD})
    public void serve(@PathVariable String publicId,
                      HttpServletRequest request,
                      HttpServletResponse response) throws IOException {
        try {
            ResourceMediaService.MediaDescriptor descriptor = resourceMediaService.resolve(publicId);
            applyCommonHeaders(response, descriptor);

            String ifNoneMatch = request.getHeader(HttpHeaders.IF_NONE_MATCH);
            if (matchesIfNoneMatch(ifNoneMatch, descriptor.etag())) {
                response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                return;
            }

            ByteRange range = requestedRange(
                    request.getHeader(HttpHeaders.RANGE),
                    request.getHeader(HttpHeaders.IF_RANGE),
                    descriptor.etag(),
                    descriptor.size()
            );
            if ("HEAD".equalsIgnoreCase(request.getMethod())) {
                verifyReadable(descriptor);
                writeMetadataResponse(response, descriptor, range);
                return;
            }

            if (range != null) {
                streamRange(response, descriptor, range, HttpServletResponse.SC_PARTIAL_CONTENT);
                return;
            }
            if (descriptor.local()) {
                streamRange(
                        response,
                        descriptor,
                        new ByteRange(0, descriptor.size() - 1),
                        HttpServletResponse.SC_OK
                );
                return;
            }

            StorageClientAccess access = resourceMediaService.resolveClientAccess(descriptor);
            response.setStatus(HttpServletResponse.SC_TEMPORARY_REDIRECT);
            response.setHeader(HttpHeaders.LOCATION, access.url());
            response.setHeader(
                    HttpHeaders.CACHE_CONTROL,
                    redirectCacheControl(access)
            );
        } catch (UnsatisfiableRangeException e) {
            writeRangeNotSatisfiable(response, e.totalLength());
        } catch (ResourceMediaAccessException e) {
            writeAccessError(response, e);
        }
    }

    private void verifyReadable(ResourceMediaService.MediaDescriptor descriptor) throws IOException {
        try (StorageRangeReadHandle handle = resourceMediaService.openRange(descriptor, 0, 0)) {
            if (handle.inputStream().read() < 0) {
                throw new IOException("物理副本首字节不可读");
            }
        }
    }

    private void writeMetadataResponse(HttpServletResponse response,
                                       ResourceMediaService.MediaDescriptor descriptor,
                                       ByteRange range) {
        if (range == null) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentLengthLong(descriptor.size());
            return;
        }
        response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
        response.setHeader(
                HttpHeaders.CONTENT_RANGE,
                "bytes " + range.start() + "-" + range.end() + "/" + descriptor.size()
        );
        response.setContentLengthLong(range.length());
    }

    private void streamRange(HttpServletResponse response,
                             ResourceMediaService.MediaDescriptor descriptor,
                             ByteRange range,
                             int status) throws IOException {
        try (StorageRangeReadHandle handle = resourceMediaService.openRange(
                descriptor,
                range.start(),
                range.end()
        )) {
            response.setStatus(status);
            if (status == HttpServletResponse.SC_PARTIAL_CONTENT) {
                response.setHeader(
                        HttpHeaders.CONTENT_RANGE,
                        "bytes " + range.start() + "-" + range.end() + "/" + descriptor.size()
                );
            }
            response.setContentLengthLong(range.length());
            copyExactly(handle.inputStream(), response.getOutputStream(), range.length());
        }
    }

    private void copyExactly(InputStream inputStream,
                             OutputStream outputStream,
                             long expectedLength) throws IOException {
        byte[] buffer = new byte[8192];
        long remaining = expectedLength;
        while (remaining > 0) {
            int read = inputStream.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (read < 0) {
                throw new IOException("物理副本在响应完成前提前结束");
            }
            outputStream.write(buffer, 0, read);
            remaining -= read;
        }
        outputStream.flush();
    }

    private void applyCommonHeaders(HttpServletResponse response,
                                    ResourceMediaService.MediaDescriptor descriptor) {
        String contentType = safeContentType(descriptor.mimeType());
        response.setHeader(HttpHeaders.ETAG, descriptor.etag());
        response.setHeader(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL);
        response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Cross-Origin-Resource-Policy", "cross-origin");
        response.setContentType(contentType);

        if (isActiveDocumentType(contentType)) {
            response.setHeader(
                    "Content-Security-Policy",
                    "sandbox; default-src 'none'; style-src 'unsafe-inline'"
            );
        }
        if (shouldForceDownload(descriptor, contentType)) {
            response.setHeader(
                    HttpHeaders.CONTENT_DISPOSITION,
                    FileDownloadUtil.contentDispositionAttachment(descriptor.originalName())
            );
        }
    }

    private String safeContentType(String value) {
        if (!StringUtils.hasText(value)) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        try {
            return MediaType.parseMediaType(value).toString();
        } catch (InvalidMediaTypeException e) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
    }

    private boolean shouldForceDownload(ResourceMediaService.MediaDescriptor descriptor,
                                        String contentType) {
        return FileDownloadUtil.shouldForceDownload(descriptor.originalName())
                || FileDownloadUtil.shouldForceDownload(descriptor.accessPath())
                || contentType.toLowerCase(Locale.ROOT).startsWith(MediaType.TEXT_HTML_VALUE)
                || contentType.toLowerCase(Locale.ROOT).startsWith("application/xhtml+xml");
    }

    private boolean isActiveDocumentType(String contentType) {
        String lower = contentType.toLowerCase(Locale.ROOT);
        return lower.startsWith("image/svg+xml")
                || lower.startsWith(MediaType.TEXT_HTML_VALUE)
                || lower.startsWith("application/xhtml+xml");
    }

    private ByteRange requestedRange(String rangeHeader,
                                     String ifRangeHeader,
                                     String etag,
                                     long totalLength) {
        if (!StringUtils.hasText(rangeHeader)) {
            return null;
        }
        if (StringUtils.hasText(ifRangeHeader) && !etag.equals(ifRangeHeader.trim())) {
            return null;
        }
        return parseRange(rangeHeader, totalLength);
    }

    private ByteRange parseRange(String header, long totalLength) {
        String value = header == null ? "" : header.trim();
        if (totalLength <= 0
                || !value.toLowerCase(Locale.ROOT).startsWith("bytes=")
                || value.indexOf(',') >= 0) {
            throw new UnsatisfiableRangeException(totalLength);
        }
        String spec = value.substring("bytes=".length()).trim();
        int dash = spec.indexOf('-');
        if (dash < 0 || dash != spec.lastIndexOf('-')) {
            throw new UnsatisfiableRangeException(totalLength);
        }
        String startText = spec.substring(0, dash).trim();
        String endText = spec.substring(dash + 1).trim();
        try {
            if (!StringUtils.hasText(startText)) {
                long suffixLength = Long.parseLong(endText);
                if (suffixLength <= 0) {
                    throw new UnsatisfiableRangeException(totalLength);
                }
                long length = Math.min(suffixLength, totalLength);
                return new ByteRange(totalLength - length, totalLength - 1);
            }

            long start = Long.parseLong(startText);
            if (start < 0 || start >= totalLength) {
                throw new UnsatisfiableRangeException(totalLength);
            }
            long end = StringUtils.hasText(endText)
                    ? Long.parseLong(endText)
                    : totalLength - 1;
            if (end < start) {
                throw new UnsatisfiableRangeException(totalLength);
            }
            return new ByteRange(start, Math.min(end, totalLength - 1));
        } catch (NumberFormatException e) {
            throw new UnsatisfiableRangeException(totalLength);
        }
    }

    private String redirectCacheControl(StorageClientAccess access) {
        if (access.privateUrl() || access.maxAgeSeconds() == 0) {
            return NO_STORE;
        }
        int maxAge = Math.min(access.maxAgeSeconds(), 60);
        return "public, max-age=" + maxAge + ", must-revalidate";
    }

    private boolean matchesIfNoneMatch(String header, String etag) {
        if (!StringUtils.hasText(header)) {
            return false;
        }
        String expected = stripWeakPrefix(etag);
        for (String candidate : header.split(",")) {
            String normalized = candidate.trim();
            if ("*".equals(normalized)
                    || expected.equals(stripWeakPrefix(normalized))) {
                return true;
            }
        }
        return false;
    }

    private String stripWeakPrefix(String etag) {
        String value = etag == null ? "" : etag.trim();
        return value.regionMatches(true, 0, "W/", 0, 2)
                ? value.substring(2).trim()
                : value;
    }

    private void writeRangeNotSatisfiable(HttpServletResponse response, long totalLength) {
        if (response.isCommitted()) {
            return;
        }
        response.reset();
        response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
        response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes */" + Math.max(totalLength, 0));
        response.setHeader(HttpHeaders.CACHE_CONTROL, NO_STORE);
        response.setHeader("X-Content-Type-Options", "nosniff");
    }

    private void writeAccessError(HttpServletResponse response, ResourceMediaAccessException error) {
        if (response.isCommitted()) {
            log.warn("稳定媒体响应提交后物理读取失败: reason={}", error.reason(), error);
            return;
        }
        response.reset();
        response.setHeader(HttpHeaders.CACHE_CONTROL, NO_STORE);
        response.setHeader("X-Content-Type-Options", "nosniff");
        if (error.reason() == ResourceMediaAccessException.Reason.NOT_FOUND) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setHeader(HttpHeaders.RETRY_AFTER, "5");
        log.debug("稳定媒体当前不可用: {}", error.getMessage());
    }

    private record ByteRange(long start, long end) {
        long length() {
            return end - start + 1;
        }
    }

    private static final class UnsatisfiableRangeException extends RuntimeException {
        private final long totalLength;

        private UnsatisfiableRangeException(long totalLength) {
            this.totalLength = totalLength;
        }

        private long totalLength() {
            return totalLength;
        }
    }
}