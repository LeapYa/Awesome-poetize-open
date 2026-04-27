package com.ld.poetry.controller;

import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.constants.CacheConstants;
import com.ld.poetry.entity.*;
import com.ld.poetry.enums.CodeMsg;
import com.ld.poetry.enums.PoetryEnum;
import com.ld.poetry.im.websocket.ImSessionManager;
import com.ld.poetry.service.CacheService;
import com.ld.poetry.service.UserService;
import com.ld.poetry.utils.PoetryUtil;
import com.ld.poetry.vo.BaseRequestVO;
import com.ld.poetry.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 后台用户 前端控制器
 * </p>
 *
 * @author sara
 * @since 2021-08-13
 */
@RestController
@RequestMapping("/admin")
@Slf4j
public class AdminUserController {

    @Autowired
    private UserService userService;

    @Autowired
    private CacheService cacheService;

    @Autowired(required = false)
    private ImSessionManager imSessionManager;

    /**
     * 查询用户
     */
    @PostMapping("/user/list")
    @LoginCheck(0)
    public PoetryResult<Page<UserVO>> listUser(@RequestBody BaseRequestVO baseRequestVO) {
        return userService.listUser(baseRequestVO);
    }

    /**
     * 修改用户状态
     * <p>
     * flag = true：解禁
     * flag = false：封禁
     */
    @GetMapping("/user/changeUserStatus")
    @LoginCheck(0)
    public PoetryResult changeUserStatus(@RequestParam("userId") Integer userId, @RequestParam("flag") Boolean flag) {
        if (userId.intValue() == PoetryUtil.getAdminUser().getId().intValue()) {
            return PoetryResult.fail("站长状态不能修改！");
        }

        LambdaUpdateChainWrapper<User> updateChainWrapper = userService.lambdaUpdate().eq(User::getId, userId);
        if (flag) {
            updateChainWrapper.eq(User::getUserStatus, PoetryEnum.STATUS_DISABLE.getCode())
                    .set(User::getUserStatus, PoetryEnum.STATUS_ENABLE.getCode()).update();
        } else {
            updateChainWrapper.eq(User::getUserStatus, PoetryEnum.STATUS_ENABLE.getCode())
                    .set(User::getUserStatus, PoetryEnum.STATUS_DISABLE.getCode()).update();
        }
        logout(userId);
        return PoetryResult.success();
    }

    /**
     * 修改用户赞赏
     */
    @GetMapping("/user/changeUserAdmire")
    @LoginCheck(0)
    public PoetryResult changeUserAdmire(@RequestParam("userId") Integer userId,
            @RequestParam("admire") String admire) {
        userService.lambdaUpdate()
                .eq(User::getId, userId)
                .set(User::getAdmire, admire)
                .update();

        // 使用CacheService清理点赞缓存
        try {
            cacheService.deleteKey(CacheConstants.ADMIRE_LIST_KEY);
        } catch (Exception e) {
            log.error("清理点赞缓存失败: userId={}", userId, e);
        }

        return PoetryResult.success();
    }

    /**
     * 修改用户类型
     */
    @GetMapping("/user/changeUserType")
    @LoginCheck(0)
    public PoetryResult changeUserType(@RequestParam("userId") Integer userId,
            @RequestParam("userType") Integer userType) {
        if (userId.intValue() == PoetryUtil.getAdminUser().getId().intValue()) {
            return PoetryResult.fail("站长类型不能修改！");
        }

        if (userType != 0 && userType != 1 && userType != 2) {
            return PoetryResult.fail(CodeMsg.PARAMETER_ERROR);
        }
        userService.lambdaUpdate().eq(User::getId, userId).set(User::getUserType, userType).update();

        logout(userId);
        return PoetryResult.success();
    }

    /**
     * 软删除用户
     */
    @GetMapping("/user/deleteUser")
    @LoginCheck(0)
    public PoetryResult deleteUser(@RequestParam("userId") Integer userId) {
        if (userId == null) {
            return PoetryResult.fail(CodeMsg.PARAMETER_ERROR);
        }

        User adminUser = PoetryUtil.getAdminUser();
        if (adminUser != null && userId.intValue() == adminUser.getId().intValue()) {
            return PoetryResult.fail("站长不能删除！");
        }

        Integer currentUserId = PoetryUtil.getUserId();
        if (currentUserId != null && userId.intValue() == currentUserId.intValue()) {
            return PoetryResult.fail("不能删除当前登录账号！");
        }

        User user = userService.getById(userId);
        if (user == null) {
            return PoetryResult.fail("用户不存在或已删除！");
        }

        boolean success = userService.removeById(userId);
        if (!success) {
            return PoetryResult.fail("删除失败！");
        }

        logout(userId);
        try {
            cacheService.deleteKey(CacheConstants.ADMIRE_LIST_KEY);
        } catch (Exception e) {
            log.error("删除用户后清理点赞缓存失败: userId={}", userId, e);
        }
        log.info("管理员软删除用户: userId={}, username={}", userId, user.getUsername());
        return PoetryResult.success();
    }

    private void logout(Integer userId) {
        try {
            log.info("管理员强制用户下线: userId={}", userId);

            // 使用CacheService统一清理所有用户token相关缓存
            cacheService.evictAllUserTokens(userId);
            cacheService.evictUser(userId);

            // 断开WebSocket连接
            if (imSessionManager != null) {
                imSessionManager.closeUserSession(userId, "管理员强制下线");
            }

            log.info("用户强制下线完成: userId={}", userId);
        } catch (Exception e) {
            log.error("强制用户下线时发生错误: userId={}", userId, e);
        }
    }
}
