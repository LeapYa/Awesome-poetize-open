package com.ld.poetry.controller;

import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.service.SysAuditLogService;
import com.ld.poetry.service.UserService;
import com.ld.poetry.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.LinkedHashMap;

/**
 * 第三方登录控制器
 */
@RestController
@RequestMapping("/api/third-party")
@Slf4j
public class ThirdPartyLoginController {

    @Autowired
    private UserService userService;

    @Autowired
    private SysAuditLogService sysAuditLogService;

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
        
        PoetryResult<UserVO> result = userService.thirdLogin(provider, uid, username, email, avatar);
        try {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("method", "THIRD_PARTY_API");
            detail.put("provider", provider);
            detail.put("reason", result.isSuccess() ? "SUCCESS" : result.getMessage());
            UserVO userVO = result.getData();
            sysAuditLogService.recordLogin("THIRD_LOGIN", result.isSuccess(),
                    email != null ? email : username,
                    userVO == null ? null : userVO.getId(),
                    userVO == null ? null : userVO.getUsername(),
                    result.isSuccess() ? "第三方服务登录成功" : "第三方服务登录失败",
                    detail);
        } catch (Exception e) {
            log.debug("记录第三方服务登录审计日志失败: {}", e.getMessage());
        }
        return result;
    }
}
