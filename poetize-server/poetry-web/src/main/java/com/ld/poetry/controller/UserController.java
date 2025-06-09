package com.ld.poetry.controller;


import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.aop.SaveCheck;
import com.ld.poetry.service.MailService;
import com.ld.poetry.service.UserService;
import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.entity.User;
import com.ld.poetry.utils.cache.PoetryCache;
import com.ld.poetry.utils.PoetryUtil;
import com.ld.poetry.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.Random;
import java.util.UUID;
import java.util.Collections;

/**
 * <p>
 * 用户信息表 前端控制器
 * </p>
 *
 * @author sara
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

    /**
     * 用户名/密码注册
     */
    @PostMapping("/regist")
    public PoetryResult<UserVO> regist(@Validated @RequestBody UserVO user) {
        return userService.regist(user);
    }


    /**
     * 用户名、邮箱、手机号/密码登录
     */
    @PostMapping("/login")
    public PoetryResult<UserVO> login(@RequestParam("account") String account,
                                      @RequestParam("password") String password,
                                      @RequestParam(value = "isAdmin", defaultValue = "false") Boolean isAdmin) {
        return userService.login(account, password, isAdmin);
    }


    /**
     * Token登录
     */
    @PostMapping("/token")
    public PoetryResult<UserVO> login(@RequestParam("userToken") String userToken) {
        return userService.token(userToken);
    }


    /**
     * 退出
     */
    @GetMapping("/logout")
    @LoginCheck
    public PoetryResult exit() {
        return userService.exit();
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
        
        // 检查token是否过期 - 修复：使用getTokenWithoutBearer()方法
        String token = PoetryUtil.getTokenWithoutBearer();
        if (token == null || token.isEmpty()) {
            return PoetryResult.fail("未登录或token无效");
        }
        
        // 检查token是否在缓存中 - 使用不带Bearer前缀的token进行缓存查找
        User cachedUser = (User) PoetryCache.get(token);
        if (cachedUser == null) {
            return PoetryResult.fail("登录已过期，请重新登录");
        }
        
        return PoetryResult.success(true);
    }


    /**
     * 更新用户信息
     */
    @PostMapping("/updateUserInfo")
    @LoginCheck
    public PoetryResult<UserVO> updateUserInfo(@RequestBody UserVO user) {
        PoetryCache.remove(CommonConst.USER_CACHE + PoetryUtil.getUserId().toString());
        return userService.updateUserInfo(user);
    }

    /**
     * 获取验证码
     * <p>
     * 1 手机号
     * 2 邮箱
     */
    @GetMapping("/getCode")
    @LoginCheck
    @SaveCheck
    public PoetryResult getCode(@RequestParam("flag") Integer flag) {
        return userService.getCode(flag);
    }

    /**
     * 绑定手机号或者邮箱
     * <p>
     * 1 手机号
     * 2 邮箱
     */
    @GetMapping("/getCodeForBind")
    @LoginCheck
    @SaveCheck
    public PoetryResult getCodeForBind(@RequestParam("place") String place, @RequestParam("flag") Integer flag) {
        return userService.getCodeForBind(place, flag);
    }

    /**
     * 更新邮箱、手机号、密码
     * <p>
     * 1 手机号
     * 2 邮箱
     * 3 密码：place=老密码&password=新密码
     */
    @PostMapping("/updateSecretInfo")
    @LoginCheck
    public PoetryResult<UserVO> updateSecretInfo(@RequestParam("place") String place, @RequestParam("flag") Integer flag, @RequestParam(value = "code", required = false) String code, @RequestParam("password") String password) {
        PoetryCache.remove(CommonConst.USER_CACHE + PoetryUtil.getUserId().toString());
        return userService.updateSecretInfo(place, flag, code, password);
    }

    /**
     * 忘记密码 获取验证码
     * <p>
     * 1 手机号
     * 2 邮箱
     */
    @GetMapping("/getCodeForForgetPassword")
    @SaveCheck
    public PoetryResult getCodeForForgetPassword(@RequestParam("place") String place, @RequestParam("flag") Integer flag) {
        return userService.getCodeForForgetPassword(place, flag);
    }

    /**
     * 忘记密码 更新密码
     * <p>
     * 1 手机号
     * 2 邮箱
     */
    @PostMapping("/updateForForgetPassword")
    public PoetryResult updateForForgetPassword(@RequestParam("place") String place, @RequestParam("flag") Integer flag, @RequestParam("code") String code, @RequestParam("password") String password) {
        return userService.updateForForgetPassword(place, flag, code, password);
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
    public PoetryResult<UserVO> subscribe(@RequestParam("labelId") Integer labelId, @RequestParam("flag") Boolean flag) {
        PoetryCache.remove(CommonConst.USER_CACHE + PoetryUtil.getUserId().toString());
        return userService.subscribe(labelId, flag);
    }

    /**
     * 第三方登录
     */
    @PostMapping("/thirdLogin")
    public PoetryResult<UserVO> thirdLogin(@RequestBody UserVO thirdUserInfo) {
        return userService.thirdLogin(
            thirdUserInfo.getPlatformType(),
            thirdUserInfo.getUid(),
            thirdUserInfo.getUsername(),
            thirdUserInfo.getEmail(),
            thirdUserInfo.getAvatar()
        );
    }
}

