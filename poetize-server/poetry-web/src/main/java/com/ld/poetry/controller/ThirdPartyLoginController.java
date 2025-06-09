package com.ld.poetry.controller;

import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.service.UserService;
import com.ld.poetry.vo.UserVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 第三方登录控制器
 */
@RestController
@RequestMapping("/api/third-party")
public class ThirdPartyLoginController {

    @Autowired
    private UserService userService;

    /**
     * 处理Python服务发送的第三方登录请求
     */
    @PostMapping("/login")
    public PoetryResult<UserVO> thirdPartyLogin(@RequestBody Map<String, String> loginData) {
        String provider = loginData.get("provider");
        String uid = loginData.get("uid");
        String username = loginData.get("username");
        String email = loginData.get("email");
        String avatar = loginData.get("avatar");
        
        return userService.thirdLogin(provider, uid, username, email, avatar);
    }
} 