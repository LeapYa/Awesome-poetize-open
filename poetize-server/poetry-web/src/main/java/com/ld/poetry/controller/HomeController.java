package com.ld.poetry.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

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
     */
    @GetMapping({"/home", "/article/**", "/user/**", "/admin/**", "/sort/**", "/label/**", "/comment/**", "/tree/**", "/weiyan/**", "/music/**", "/picture/**", "/video/**", "/love/**", "/funny/**", "/favorites/**", "/im/**"})
    public String forwardToIndex() {
        return "forward:/index.html";
    }
} 