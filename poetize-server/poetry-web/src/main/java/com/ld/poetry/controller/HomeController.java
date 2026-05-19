package com.ld.poetry.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.MediaType;

/**
 * 首页控制器
 * 处理前端路由请求，返回index.html
 */
@Controller
public class HomeController {

    /**
     * 处理根路径请求，返回前端页面
     */
    @GetMapping("/")
    public String index() {
        return "forward:/index.html";
    }

    /**
     * 处理前端路由请求，避免刷新404
     * 注意：排除 /api/ 开头的路径，避免拦截API请求
     */
    @GetMapping({ "/home", "/user/**", "/admin/**", "/sort/**", "/label/**", "/comment/**", "/tree/**", "/weiyan/**",
            "/music/**", "/picture/**", "/video/**", "/love/**", "/funny/**", "/favorites/**", "/im/**" })
    public String forwardToIndex() {
        return "forward:/index.html";
    }

    /**
     * 数字文章地址也可能是前端文章页，浏览器刷新时优先交给 SPA。
     */
    @GetMapping(value = "/article/{id:\\d+}", headers = "Accept=text/html", produces = MediaType.TEXT_HTML_VALUE)
    public String forwardNumericArticleToIndex() {
        return "forward:/index.html";
    }

    /**
     * 单独处理前端文章路由，避免拦截 /article 下的 JSON API。
     * 只在浏览器请求 HTML 页面时转发，如 /article/123、/article/slug、/article/en/slug。
     */
    @GetMapping(value = {
            "/article/{path:(?!\\d+$).+}",
            "/article/{lang:[a-zA-Z]{2,8}(?:-[a-zA-Z]{2,8})?}/{path}"
    }, headers = "Accept=text/html", produces = MediaType.TEXT_HTML_VALUE)
    public String forwardArticleToIndex() {
        return "forward:/index.html";
    }
}
