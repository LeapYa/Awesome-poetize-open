package com.ld.poetry.controller;

import com.ld.poetry.dao.ResourceRedirectMapper;
import com.ld.poetry.entity.ResourceRedirect;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

@RestController
@RequiredArgsConstructor
public class ResourceRedirectController {

    private final ResourceRedirectMapper resourceRedirectMapper;

    @GetMapping("/resource/redirect")
    public void redirect(
            @RequestHeader(value = "X-Resource-Source-Path", required = false) String sourcePathHeader,
            @RequestParam(value = "path", required = false) String sourcePathParam,
            HttpServletResponse response) throws IOException {
        String encodedSourcePath = StringUtils.hasText(sourcePathHeader) ? sourcePathHeader : sourcePathParam;
        String sourcePath = decodePath(stripQueryString(encodedSourcePath));
        if (!isAllowedSourcePath(sourcePath)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        ResourceRedirect redirect = resourceRedirectMapper.findActiveBySourcePath(sourcePath);
        if (redirect == null || !isSafeTargetUrl(redirect.getTargetUrl())) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader(HttpHeaders.LOCATION, redirect.getTargetUrl());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "public, max-age=300");
        response.setHeader("X-Content-Type-Options", "nosniff");
    }

    private String stripQueryString(String path) {
        if (!StringUtils.hasText(path)) {
            return "";
        }
        int queryIndex = path.indexOf('?');
        return queryIndex >= 0 ? path.substring(0, queryIndex) : path;
    }

    private String decodePath(String path) {
        try {
            return UriUtils.decode(path, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private boolean isAllowedSourcePath(String sourcePath) {
        return StringUtils.hasText(sourcePath)
                && sourcePath.length() <= 1024
                && sourcePath.startsWith("/static/")
                && !sourcePath.contains("\r")
                && !sourcePath.contains("\n")
                && !sourcePath.contains("../")
                && !sourcePath.contains("\\");
    }

    private boolean isSafeTargetUrl(String targetUrl) {
        if (!StringUtils.hasText(targetUrl)
                || targetUrl.contains("\r")
                || targetUrl.contains("\n")) {
            return false;
        }
        try {
            URI uri = URI.create(targetUrl);
            String scheme = String.valueOf(uri.getScheme()).toLowerCase();
            return ("http".equals(scheme) || "https".equals(scheme))
                    && StringUtils.hasText(uri.getHost());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}