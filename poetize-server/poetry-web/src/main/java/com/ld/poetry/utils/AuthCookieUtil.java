package com.ld.poetry.utils;

import com.ld.poetry.constants.CommonConst;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class AuthCookieUtil {

    public void writeAuthCookie(HttpServletRequest request, HttpServletResponse response, String token) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(request, CryptoUtil.encrypt(token), CommonConst.TOKEN_EXPIRE).toString());
    }

    public void clearAuthCookie(HttpServletRequest request, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(request, "", 0).toString());
    }

    public String resolveToken(HttpServletRequest request) {
        if (request == null || request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (CommonConst.AUTH_COOKIE.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                String rawValue = cookie.getValue().trim();
                String decrypted = CryptoUtil.decrypt(rawValue);
                if (decrypted != null) {
                    return decrypted;
                }
                // 解密失败视为无效
                log.warn("Token 解密失败，cookie 值无效或为旧格式");
                return null;
            }
        }
        return null;
    }

    private ResponseCookie buildCookie(HttpServletRequest request, String token, long maxAgeSeconds) {
        return ResponseCookie.from(CommonConst.AUTH_COOKIE, token)
                .httpOnly(true)
                .secure(isSecureRequest(request))
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
    }

    private boolean isSecureRequest(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        if (request.isSecure()) {
            return true;
        }
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        return "https".equalsIgnoreCase(forwardedProto);
    }
}
