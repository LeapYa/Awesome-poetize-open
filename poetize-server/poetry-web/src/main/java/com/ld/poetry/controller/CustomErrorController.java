package com.ld.poetry.controller;

import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.enums.CodeMsg;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 自定义错误控制器，替代 Spring Boot 默认的 BasicErrorController。
 * 避免暴露框架特征（如 Whitelabel Error Page、默认 JSON 错误格式中的 timestamp/status/error/path 字段）。
 */
@RestController
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public PoetryResult<Void> handleError(HttpServletRequest request, HttpServletResponse response) {
        int status = response.getStatus();

        return switch (status) {
            case 404 -> PoetryResult.fail(404, "请求的资源不存在");
            case 405 -> PoetryResult.fail(405, "请求方法不允许");
            case 401 -> PoetryResult.fail(CodeMsg.LOGIN_EXPIRED);
            case 403 -> PoetryResult.fail(CodeMsg.FORBIDDEN);
            case 429 -> PoetryResult.fail(CodeMsg.RATE_LIMITED);
            default -> PoetryResult.fail(CodeMsg.FAIL);
        };
    }
}
