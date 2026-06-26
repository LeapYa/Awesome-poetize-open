package com.ld.poetry.controller;

import com.ld.poetry.aop.AuditLog;
import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.aop.RateLimit;
import com.ld.poetry.aop.RateLimit.KeyType;
import com.ld.poetry.aop.RateLimits;
import com.ld.poetry.aop.SaveCheck;
import com.ld.poetry.entity.User;
import com.ld.poetry.service.CacheService;
import com.ld.poetry.service.CaptchaService;
import com.ld.poetry.service.MailService;
import com.ld.poetry.service.SysAuditLogService;
import com.ld.poetry.service.UserService;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.enums.CodeMsg;
import com.ld.poetry.utils.JsonUtils;
import com.ld.poetry.utils.PoetryUtil;
import com.ld.poetry.utils.CryptoUtil;
import com.ld.poetry.utils.AuthCookieUtil;
import com.ld.poetry.vo.EncryptedRequestVO;
import com.ld.poetry.vo.EncryptedResponseVO;
import com.ld.poetry.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 用户信息表 前端控制器
 * </p>
 *
 * @author sara/LeapYa
 * @since 2021-08-12
 */
@RestController
@RequestMapping("/user")
@Slf4j
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private MailService mailService;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private CaptchaService captchaService;

    @Autowired
    private AuthCookieUtil authCookieUtil;

    @Autowired
    private SysAuditLogService sysAuditLogService;

    /**
     * 用户名/密码注册
     * 
     * 限流规则：
     * - 指纹维度：5次/5分钟（防自动化注册）
     * - IP维度：50次/分钟（宽松兜底，防DDoS）
     */
    @PostMapping("/regist")
    @RateLimits({
            @RateLimit(name = "regist:fp", count = 5, time = 300, keyType = KeyType.FINGERPRINT, message = "注册操作过于频繁，请5分钟后再试"),
            @RateLimit(name = "regist:ip", count = 50, time = 60, keyType = KeyType.IP, message = "当前网络注册请求过多，请稍后再试")
    })
    @AuditLog(type = "SECURITY", action = "USER_REGISTER", targetType = "USER", targetIdParam = "username", summary = "用户注册")
    public PoetryResult<UserVO> regist(@Validated @RequestBody UserVO user,
            HttpServletRequest request,
            HttpServletResponse response) {
        // 检查是否需要验证码（使用register配置项）
        boolean captchaRequired = captchaService.isCaptchaRequired("register");
        String verificationToken = user.getVerificationToken();

        if (captchaRequired) {
            // 验证码开启时，必须提供有效token
            if (verificationToken == null || verificationToken.isEmpty()) {
                log.warn("注册需要验证码但未提供token，拒绝请求");
                return PoetryResult.fail(CodeMsg.CAPTCHA_REQUIRED.getCode(), "请先完成验证码验证");
            }

            boolean isTokenValid = captchaService.verifyToken("register", verificationToken, null, null);
            if (!isTokenValid) {
                log.warn("注册验证码token验证失败，拒绝注册请求");
                return PoetryResult.fail(CodeMsg.CAPTCHA_INVALID.getCode(), "验证码验证失败，请重新验证后再试");
            }
            log.info("注册验证码token验证通过");
        }

        PoetryResult<UserVO> result = userService.regist(user);
        return attachCookieIfNeeded(result, request, response);
    }

    /**
     * 用户名、邮箱、手机号/密码登录
     * 
     * 限流规则：
     * - 指纹维度：20次/5分钟（防暴力破解）
     * - IP维度：100次/分钟（宽松兜底，防DDoS）
     */
    @PostMapping("/login")
    @RateLimits({
            @RateLimit(name = "login:fp", count = 20, time = 300, keyType = KeyType.FINGERPRINT, message = "登录尝试过于频繁，请5分钟后再试"),
            @RateLimit(name = "login:ip", count = 100, time = 60, keyType = KeyType.IP, message = "当前网络登录请求过多，请稍后再试")
    })
    public PoetryResult<EncryptedResponseVO> login(@RequestParam(value = "account", required = false) String account,
            @RequestParam(value = "password", required = false) String password,
            @RequestParam(value = "isAdmin", defaultValue = "false") Boolean isAdmin,
            @RequestBody(required = false) EncryptedRequestVO encryptedRequest,
            HttpServletRequest request,
            HttpServletResponse response) {
        String verificationToken = null;

        // 如果有加密请求体，则解密并提取参数
        if (encryptedRequest != null && encryptedRequest.getData() != null) {
            try {
                // 使用安全的AES-GCM模式解密请求体
                String decryptedData = CryptoUtil.decrypt(encryptedRequest.getData());
                if (decryptedData == null) {
                    recordPasswordLogin(false, account, null, "登录请求解密失败", "DECRYPT_FAILED", isAdmin);
                    return PoetryResult.fail("解密失败");
                }

                // 解析JSON
                Map<String, Object> params = JsonUtils.parseObject(decryptedData, Map.class);

                // 从解密后的数据中提取参数
                account = (String) params.get("account");
                password = (String) params.get("password");
                isAdmin = params.get("isAdmin") != null ? (Boolean) params.get("isAdmin") : false;
                verificationToken = (String) params.get("verificationToken");
            } catch (Exception e) {
                log.error("解密登录请求失败", e);
                recordPasswordLogin(false, account, null, "登录请求解密失败", "DECRYPT_EXCEPTION", isAdmin);
                return PoetryResult.fail("请求解密失败");
            }
        }

        // 验证必要参数
        if (account == null || password == null) {
            recordPasswordLogin(false, account, null, "登录参数不完整", "MISSING_ACCOUNT_OR_PASSWORD", isAdmin);
            return PoetryResult.fail("账号和密码不能为空");
        }

        // 检查是否需要验证码
        boolean captchaRequired = captchaService.isCaptchaRequired("login");

        if (captchaRequired) {
            // 验证码开启时，必须提供有效token
            if (verificationToken == null || verificationToken.isEmpty()) {
                log.warn("登录需要验证码但未提供token，拒绝请求");
                recordPasswordLogin(false, account, null, "登录验证码未完成", "CAPTCHA_REQUIRED", isAdmin);
                return PoetryResult.fail(CodeMsg.CAPTCHA_REQUIRED.getCode(), "请先完成验证码验证");
            }

            boolean isTokenValid = captchaService.verifyToken("login", verificationToken, null, null);
            if (!isTokenValid) {
                log.warn("登录验证码token验证失败，拒绝登录请求");
                recordPasswordLogin(false, account, null, "登录验证码验证失败", "CAPTCHA_INVALID", isAdmin);
                return PoetryResult.fail(CodeMsg.CAPTCHA_INVALID.getCode(), "验证码验证失败，请重新验证后再试");
            }
            log.info("登录验证码token验证通过");
        }

        // 调用登录服务
        PoetryResult<UserVO> loginResult = userService.login(account, password, isAdmin);
        loginResult = attachCookieIfNeeded(loginResult, request, response);
        if (loginResult.getCode() == 200 && loginResult.getData() != null) {
            recordPasswordLogin(true, account, loginResult.getData(), "账号密码登录成功", "SUCCESS", isAdmin);
        } else {
            recordPasswordLogin(false, account, null, "账号密码登录失败", loginResult.getMessage(), isAdmin);
        }

        // 如果登录成功，对响应数据进行加密
        if (loginResult.getCode() == 200 && loginResult.getData() != null) {
            try {
                // 将UserVO转换为JSON字符串
                String responseData = JsonUtils.toJsonString(loginResult.getData());

                // 使用安全的AES-GCM模式加密响应数据
                String encryptedData = CryptoUtil.encrypt(responseData);
                if (encryptedData == null) {
                    log.error("加密登录响应失败");
                    // 加密失败时返回原始数据
                    return PoetryResult.success(new EncryptedResponseVO(JsonUtils.toJsonString(loginResult.getData())));
                }

                // 返回加密后的响应
                return PoetryResult.success(new EncryptedResponseVO(encryptedData));
            } catch (Exception e) {
                log.error("加密登录响应失败", e);
                // 加密失败时返回原始数据
                return PoetryResult.success(new EncryptedResponseVO(JsonUtils.toJsonString(loginResult.getData())));
            }
        }

        // 登录失败时，直接返回错误信息
        return PoetryResult.fail(loginResult.getMessage());
    }

    /**
     * Token登录
     * 
     * 限流规则：
     * - IP维度：50次/分钟（Token验证通常自动化，阈值适中）
     */
    @PostMapping("/token")
    @RateLimit(name = "token:ip", count = 50, time = 60, keyType = KeyType.IP, message = "Token验证请求过于频繁，请稍后再试")
    public PoetryResult<UserVO> login(@RequestParam(value = "userToken", required = false) String userToken) {
        return userService.token(userToken);
    }

    /**
     * 退出
     * 使用 allowExpired=true，即使 token 过期也允许退出
     */
    @GetMapping("/logout")
    @LoginCheck(allowExpired = true)
    public PoetryResult exit(HttpServletRequest request, HttpServletResponse response) {
        User currentUser = PoetryUtil.getCurrentUser();
        try {
            PoetryResult result = userService.exit();
            authCookieUtil.clearAuthCookie(request, response);
            recordLogout(true, currentUser, "退出登录成功", result.getMessage());
            return result;
        } catch (Exception e) {
            // 即使在异常情况下也返回成功，因为退出操作应该总是成功
            log.warn("退出登录过程中发生异常，但返回成功: {}", e.getMessage());
            recordLogout(false, currentUser, "退出登录异常", e.getMessage());
            return PoetryResult.success("退出成功");
        }
    }

    /**
     * 检查是否拥有站长权限
     */
    @GetMapping("/checkAdminAuth")
    @LoginCheck(0)
    public PoetryResult<Boolean> checkAdminAuth() {
        // 获取当前用户
        User user = PoetryUtil.getCurrentUser();

        // 检查是否站长或管理员账号
        if (user.getUserType() != 0 && user.getUserType() != 1) {
            return PoetryResult.fail("权限不足");
        }

        // 检查token是否过期 - 使用Redis缓存验证
        String token = PoetryUtil.getTokenWithoutBearer();
        if (token == null || token.isEmpty()) {
            return PoetryResult.fail("未登录或token无效");
        }

        try {
            // 使用CacheService检查token是否在Redis缓存中
            Integer userId = cacheService.getUserIdFromSession(token);
            if (userId == null) {
                return PoetryResult.fail("登录已过期，请重新登录");
            }

            // 验证用户信息是否存在
            User cachedUser = cacheService.getCachedUser(userId);
            if (cachedUser == null) {
                return PoetryResult.fail("用户信息已过期，请重新登录");
            }

        } catch (Exception e) {
            log.error("Token验证时发生错误: tokenPrefix={}", com.ld.poetry.utils.TokenValidationUtil.getTokenPrefix(token), e);
            return PoetryResult.fail("Token验证失败，请重新登录");
        }

        return PoetryResult.success(true);
    }

    /**
     * 更新用户信息
     */
    @PostMapping("/updateUserInfo")
    @LoginCheck
    @AuditLog(action = "USER_INFO_UPDATE", targetType = "USER", targetIdParam = "id", summary = "更新用户信息")
    public PoetryResult<UserVO> updateUserInfo(@RequestBody UserVO user) {
        return userService.updateUserInfo(user);
    }

    /**
     * 获取验证码（已登录用户）
     * <p>
     * 1 手机号
     * 2 邮箱
     * 
     * 限流规则（分层递进，正常用户只触发第一层）：
     * - 用户维度：1次/60秒（核心规则，同一用户60秒只能发一次）
     */
    @GetMapping("/getCode")
    @LoginCheck
    @SaveCheck
    @RateLimit(name = "getCode:user", count = 1, time = 60, keyType = KeyType.USER, message = "验证码发送过于频繁，请60秒后再试")
    public PoetryResult getCode(@RequestParam("flag") Integer flag) {
        return userService.getCode(flag);
    }

    /**
     * 注册获取验证码（未登录用户）
     * <p>
     * 1 手机号
     * 2 邮箱
     *
     * 限流规则（分层递进）：
     * - 目标维度：1次/60秒（核心规则，同一邮箱/手机60秒只能发一次）
     * - 指纹维度：10次/小时（防止换目标批量攻击）
     */
    @GetMapping("/getCodeForRegister")
    @SaveCheck
    @RateLimits({
            @RateLimit(name = "sendCode:target", count = 1, time = 60, keyType = KeyType.CUSTOM, key = "#place", message = "该邮箱/手机号验证码发送过于频繁，请60秒后再试"),
            @RateLimit(name = "sendCode:fp", count = 10, time = 3600, keyType = KeyType.FINGERPRINT, message = "验证码发送次数过多，请1小时后再试")
    })
    public PoetryResult getCodeForRegister(@RequestParam("place") String place, @RequestParam("flag") Integer flag) {
        return userService.getCodeForRegister(place, flag);
    }

    /**
     * 绑定手机号或者邮箱
     * <p>
     * 1 手机号
     * 2 邮箱
     * 
     * 限流规则（分层递进）：
     * - 目标维度：1次/60秒（核心规则，同一邮箱/手机60秒只能发一次）
     * - 指纹维度：10次/小时（防止换目标批量攻击）
     */
    @GetMapping("/getCodeForBind")
    @LoginCheck
    @SaveCheck
    @RateLimits({
            @RateLimit(name = "sendCode:target", count = 1, time = 60, keyType = KeyType.CUSTOM, key = "#place", message = "该邮箱/手机号验证码发送过于频繁，请60秒后再试"),
            @RateLimit(name = "sendCode:fp", count = 10, time = 3600, keyType = KeyType.FINGERPRINT, message = "验证码发送次数过多，请1小时后再试")
    })
    public PoetryResult getCodeForBind(@RequestParam("place") String place, @RequestParam("flag") Integer flag) {
        return userService.getCodeForBind(place, flag);
    }

    /**
     * 修改密钥信息(手机号、邮箱、密码)
     * <p>
     * 1 手机号
     * 2 邮箱
     * 3 密码：place=老密码&password=新密码
     */
    @PostMapping("/updateSecretInfo")
    @LoginCheck
    @AuditLog(type = "SECURITY", action = "USER_SECRET_UPDATE", targetType = "USER", summary = "修改密钥信息")
    public PoetryResult<UserVO> updateSecretInfo(@RequestParam("place") String place,
            @RequestParam("flag") Integer flag, @RequestParam(value = "code", required = false) String code,
            @RequestParam("password") String password) {
        return userService.updateSecretInfo(place, flag, code, password);
    }

    /**
     * 忘记密码 获取验证码
     * <p>
     * 1 手机号
     * 2 邮箱
     * 
     * 限流规则（分层递进）：
     * - 目标维度：1次/60秒（核心规则，同一邮箱/手机60秒只能发一次）
     * - 指纹维度：10次/小时（防止换目标批量攻击）
     */
    @GetMapping("/getCodeForForgetPassword")
    @SaveCheck
    @RateLimits({
            @RateLimit(name = "sendCode:target", count = 1, time = 60, keyType = KeyType.CUSTOM, key = "#place", message = "该邮箱/手机号验证码发送过于频繁，请60秒后再试"),
            @RateLimit(name = "sendCode:fp", count = 10, time = 3600, keyType = KeyType.FINGERPRINT, message = "验证码发送次数过多，请1小时后再试")
    })
    public PoetryResult getCodeForForgetPassword(@RequestParam("username") String username,
            @RequestParam("place") String place,
            @RequestParam("flag") Integer flag) {
        return userService.getCodeForForgetPassword(username, place, flag);
    }

    /**
     * 忘记密码 更新密码
     * <p>
     * 1 手机号
     * 2 邮箱
     * 
     * 限流规则：
     * - 指纹维度：5次/5分钟（防暴力尝试）
     * - IP维度：20次/5分钟（适中阈值）
     */
    @PostMapping("/updateForForgetPassword")
    @RateLimits({
            @RateLimit(name = "resetPassword:fp", count = 5, time = 300, keyType = KeyType.FINGERPRINT, message = "密码重置尝试过于频繁，请5分钟后再试"),
            @RateLimit(name = "resetPassword:ip", count = 20, time = 300, keyType = KeyType.IP, message = "当前网络密码重置请求过多，请稍后再试")
    })
    @AuditLog(type = "SECURITY", action = "USER_PASSWORD_RESET", targetType = "USER", targetIdParam = "username", summary = "忘记密码重置")
    public PoetryResult updateForForgetPassword(@RequestParam("username") String username,
            @RequestParam("place") String place, @RequestParam("flag") Integer flag,
            @RequestParam("code") String code, @RequestParam("password") String password) {
        return userService.updateForForgetPassword(username, place, flag, code, password);
    }

    /**
     * 根据用户名查找用户信息
     */
    @GetMapping("/getUserByUsername")
    @LoginCheck
    public PoetryResult<List<UserVO>> getUserByUsername(@RequestParam("username") String username) {
        return userService.getUserByUsername(username);
    }

    /**
     * 订阅/取消订阅专栏（标签）
     * <p>
     * flag = true：订阅
     * flag = false：取消订阅
     */
    @GetMapping("/subscribe")
    @LoginCheck
    public PoetryResult<UserVO> subscribe(@RequestParam("labelId") Integer labelId,
            @RequestParam("flag") Boolean flag) {
        // 先执行订阅操作
        PoetryResult<UserVO> result = userService.subscribe(labelId, flag);

        // 订阅操作成功后更新缓存中的用户信息
        if (result.getCode() == 200 && result.getData() != null) {
            try {
                Integer userId = PoetryUtil.getUserId();

                // 从数据库重新获取最新的用户信息
                User updatedUser = userService.getById(userId);
                if (updatedUser != null) {
                    // 重新缓存更新后的用户信息，而不是简单删除缓存
                    cacheService.cacheUser(updatedUser);
                } else {
                    // 如果获取不到用户信息，则清除缓存
                    cacheService.evictUser(userId);
                    log.warn("无法获取更新后的用户信息，清除缓存: userId={}", userId);
                }
            } catch (Exception e) {
                log.error("更新用户订阅信息缓存时发生错误: userId={}, labelId={}, flag={}", PoetryUtil.getUserId(), labelId, flag, e);
                // 缓存更新失败不影响订阅操作的结果
            }
        }

        return result;
    }

    /**
     * 第三方登录
     * 
     * 限流规则：
     * - 指纹维度：20次/5分钟（防自动化攻击）
     * - IP维度：100次/分钟（宽松兜底）
     */
    @PostMapping("/thirdLogin")
    @RateLimits({
            @RateLimit(name = "thirdLogin:fp", count = 20, time = 300, keyType = KeyType.FINGERPRINT, message = "登录尝试过于频繁，请5分钟后再试"),
            @RateLimit(name = "thirdLogin:ip", count = 100, time = 60, keyType = KeyType.IP, message = "当前网络登录请求过多，请稍后再试")
    })
    public PoetryResult<UserVO> thirdLogin(@RequestBody UserVO thirdUserInfo,
            HttpServletRequest request,
            HttpServletResponse response) {
        // 检查是否需要验证码
        boolean captchaRequired = captchaService.isCaptchaRequired("login");
        String verificationToken = thirdUserInfo.getVerificationToken();

        if (captchaRequired) {
            // 验证码开启时，必须提供有效token
            if (verificationToken == null || verificationToken.isEmpty()) {
                log.warn("第三方登录需要验证码但未提供token，拒绝请求");
                recordThirdLogin(false, thirdUserInfo, null, "第三方登录验证码未完成", "CAPTCHA_REQUIRED");
                return PoetryResult.fail(CodeMsg.CAPTCHA_REQUIRED.getCode(), "请先完成验证码验证");
            }

            boolean isTokenValid = captchaService.verifyToken("login", verificationToken, null, null);
            if (!isTokenValid) {
                log.warn("第三方登录验证码token验证失败，拒绝登录请求");
                recordThirdLogin(false, thirdUserInfo, null, "第三方登录验证码验证失败", "CAPTCHA_INVALID");
                return PoetryResult.fail(CodeMsg.CAPTCHA_INVALID.getCode(), "验证码验证失败，请重新验证后再试");
            }
            log.info("第三方登录验证码token验证通过");
        }

        PoetryResult<UserVO> result = userService.thirdLogin(
                thirdUserInfo.getPlatformType(),
                thirdUserInfo.getUid(),
                thirdUserInfo.getUsername(),
                thirdUserInfo.getEmail(),
                thirdUserInfo.getAvatar());
        if (result.getCode() == 200 && result.getData() != null) {
            recordThirdLogin(true, thirdUserInfo, result.getData(), "第三方登录成功", "SUCCESS");
        } else {
            recordThirdLogin(false, thirdUserInfo, null, "第三方登录失败", result.getMessage());
        }
        return attachCookieIfNeeded(result, request, response);
    }

    private void recordPasswordLogin(boolean success, String account, UserVO userVO, String summary,
                                     String reason, Boolean isAdmin) {
        try {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("method", "PASSWORD");
            detail.put("reason", reason);
            detail.put("adminRequested", Boolean.TRUE.equals(isAdmin));
            sysAuditLogService.recordLogin("PASSWORD_LOGIN", success, account,
                    userVO == null ? null : userVO.getId(),
                    userVO == null ? null : userVO.getUsername(),
                    summary,
                    detail);
        } catch (Exception e) {
            log.debug("记录账号密码登录审计日志失败: {}", e.getMessage());
        }
    }

    private void recordThirdLogin(boolean success, UserVO thirdUserInfo, UserVO userVO, String summary, String reason) {
        try {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("method", "THIRD_LOGIN");
            detail.put("reason", reason);
            detail.put("provider", thirdUserInfo == null ? null : thirdUserInfo.getPlatformType());
            String account = null;
            if (thirdUserInfo != null) {
                account = thirdUserInfo.getEmail() != null ? thirdUserInfo.getEmail() : thirdUserInfo.getUsername();
                if (account == null) {
                    account = thirdUserInfo.getUid();
                }
            }
            sysAuditLogService.recordLogin("THIRD_LOGIN", success, account,
                    userVO == null ? null : userVO.getId(),
                    userVO == null ? null : userVO.getUsername(),
                    summary,
                    detail);
        } catch (Exception e) {
            log.debug("记录第三方登录审计日志失败: {}", e.getMessage());
        }
    }

    private void recordLogout(boolean success, User user, String summary, String reason) {
        try {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("method", "LOGOUT");
            detail.put("reason", reason);
            sysAuditLogService.recordLogin("LOGOUT", success,
                    user == null ? null : user.getUsername(),
                    user == null ? null : user.getId(),
                    user == null ? null : user.getUsername(),
                    summary,
                    detail);
        } catch (Exception e) {
            log.debug("记录退出登录审计日志失败: {}", e.getMessage());
        }
    }

    private PoetryResult<UserVO> attachCookieIfNeeded(PoetryResult<UserVO> result,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (result != null && result.getCode() == 200 && result.getData() != null) {
            String accessToken = result.getData().getAccessToken();
            if (accessToken != null && !accessToken.isBlank()) {
                authCookieUtil.writeAuthCookie(request, response, accessToken);
                result.getData().setAccessToken(null);
            }
        }
        return result;
    }
}
