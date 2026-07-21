package com.ld.poetry.service.impl;

import com.ld.poetry.utils.JsonUtils;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.constants.CacheConstants;
import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.dao.UserMapper;
import com.ld.poetry.entity.ThirdPartyOauthConfig;
import com.ld.poetry.entity.User;
import com.ld.poetry.entity.WebInfo;
import com.ld.poetry.entity.WeiYan;
import com.ld.poetry.enums.PoetryEnum;
import com.ld.poetry.handle.PoetryRuntimeException;
import com.ld.poetry.im.http.dao.ImChatGroupUserMapper;
import com.ld.poetry.im.http.dao.ImChatUserFriendMapper;
import com.ld.poetry.im.http.entity.ImChatGroupUser;
import com.ld.poetry.im.http.entity.ImChatUserFriend;
import com.ld.poetry.im.websocket.ImConfigConst;
import com.ld.poetry.service.CacheService;
import com.ld.poetry.service.PasswordService;
import com.ld.poetry.service.PasswordUpgradeService;
import com.ld.poetry.service.SysConfigService;
import com.ld.poetry.service.ThirdPartyOauthConfigService;
import com.ld.poetry.service.UserService;
import com.ld.poetry.service.WeiYanService;
import com.ld.poetry.utils.*;
import com.ld.poetry.utils.mail.MailUtil;
import com.ld.poetry.vo.BaseRequestVO;
import com.ld.poetry.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 用户信息表 服务实现类
 * </p>
 *
 * @author sara
 * @since 2021-08-12
 */
@SuppressWarnings("unchecked")
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Autowired
    private WeiYanService weiYanService;

    @Autowired
    private com.ld.poetry.plugin.PluginHookManager pluginHookManager;

    @Autowired
    private ImChatGroupUserMapper imChatGroupUserMapper;

    @Autowired
    private ImChatUserFriendMapper imChatUserFriendMapper;

    @Autowired
    private MailUtil mailUtil;

    @Autowired
    private com.ld.poetry.utils.cache.UserCacheManager userCacheManager;

    @Autowired
    private ThirdPartyOauthConfigService thirdPartyOauthConfigService;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private SysConfigService sysConfigService;

    @Autowired
    private com.ld.poetry.service.OAuthClientService oAuthClientService;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private PasswordUpgradeService passwordUpgradeService;

    /**
     * 检查IP是否在管理员IP白名单中
     * 
     * @param ip 客户端IP
     * @return 是否在白名单中
     */
    private boolean isIpInAdminWhitelist(String ip) {
        try {
            String whitelistKey = CacheConstants.CACHE_PREFIX + "admin:ip:whitelist";
            Object cached = cacheService.get(whitelistKey);
            @SuppressWarnings("unchecked")
            Set<String> whitelistedIps = cached instanceof Set ? (Set<String>) cached : null;

            if (whitelistedIps == null || whitelistedIps.isEmpty()) {
                // 初始化时没有白名单，允许第一次登录设置
                return true;
            }
            return whitelistedIps.contains(ip);
        } catch (Exception e) {
            log.error("检查管理员IP白名单时发生错误: ip={}", ip, e);
            return true; // 发生错误时允许登录
        }
    }

    /**
     * 记录管理员登录IP
     * 
     * @param ip 客户端IP
     */
    private void recordAdminLoginIp(String ip) {
        try {
            String whitelistKey = CacheConstants.CACHE_PREFIX + "admin:ip:whitelist";
            Object cached = cacheService.get(whitelistKey);
            @SuppressWarnings("unchecked")
            Set<String> whitelistedIps = cached instanceof Set ? (Set<String>) cached : new HashSet<>();

            whitelistedIps.add(ip);
            cacheService.set(whitelistKey, whitelistedIps, CacheConstants.VERY_LONG_EXPIRE_TIME);
        } catch (Exception e) {
            log.error("记录管理员登录IP时发生错误: ip={}", ip, e);
        }
    }

    /**
     * 站长登录后清理登录前5分钟内未登录状态产生的访问记录。
     * 仅清理记录，不再将IP加入忽略名单（站长访问通过会话动态识别）。
     */
    private void handleOwnerVisitIp(String ip) {
        try {
            cacheService.ignoreVisitIpAndCleanRecent(ip, 5);
        } catch (Exception e) {
            log.warn("清理站长登录前访问记录失败: ip={}, error={}", ip, e.getMessage());
        }
    }

    /**
     * 检查账号是否被锁定
     * 
     * @param account 账号
     * @return 是否被锁定
     */
    private boolean isAccountLocked(String account) {
        try {
            String lockKey = CacheConstants.CACHE_PREFIX + "login:lock:" + account;
            return cacheService.get(lockKey) != null;
        } catch (Exception e) {
            log.error("检查账号锁定状态时发生错误: account={}", account, e);
            return false; // 发生错误时不阻止登录
        }
    }

    /**
     * 记录失败登录尝试
     * 
     * @param account 账号
     * @return 当前尝试次数
     */
    private int recordFailedLoginAttempt(String account) {
        try {
            String attemptKey = CacheConstants.buildLoginAttemptKey(account);
            Object attemptsObj = cacheService.get(attemptKey);
            Integer attempts = attemptsObj != null ? (Integer) attemptsObj : 0;
            attempts++;

            // 设置失败尝试记录，过期时间1小时
            cacheService.set(attemptKey, attempts, 3600);

            // 如果失败次数超过阈值，锁定账号
            if (attempts >= CommonConst.MAX_LOGIN_ATTEMPTS) {
                String lockKey = CacheConstants.CACHE_PREFIX + "login:lock:" + account;
                cacheService.set(lockKey, true, CommonConst.LOGIN_LOCKOUT_TIME);
                log.warn("账号 {} 因多次登录失败被锁定 {} 秒", account, CommonConst.LOGIN_LOCKOUT_TIME);
            }

            return attempts;
        } catch (Exception e) {
            log.error("记录登录失败尝试时发生错误: account={}", account, e);
            return 0;
        }
    }

    /**
     * 清除失败登录尝试记录
     * 
     * @param account 账号
     */
    private void clearFailedLoginAttempts(String account) {
        try {
            String attemptKey = CacheConstants.buildLoginAttemptKey(account);
            cacheService.deleteKey(attemptKey);
        } catch (Exception e) {
            log.error("清除登录失败尝试记录失败: {}", account, e);
        }
    }

    @Override
    public PoetryResult<UserVO> login(String account, String password, Boolean isAdmin) {
        // 获取客户端IP
        String clientIp = PoetryUtil.getIpAddr(PoetryUtil.getRequest());

        // 检查账号是否被锁定
        if (isAccountLocked(account)) {
            log.warn("账号已被锁定 - 账号: {}, IP: {}", account, clientIp);
            return PoetryResult.fail("账号已被锁定，请稍后再试");
        }

        // 验证用户名/邮箱/手机号和密码
        User one = null;

        // 尝试通过用户名查找
        one = lambdaQuery().eq(User::getUsername, account).one();

        // 如果用户名未找到，且输入格式符合邮箱规则，尝试通过邮箱查找
        if (one == null && account.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")) {
            one = lambdaQuery().eq(User::getEmail, account).one();
        }

        // 如果用户名和邮箱都未找到，且输入格式符合手机号规则，尝试通过手机号查找
        if (one == null && account.matches("^1[3-9]\\d{9}$")) {
            one = lambdaQuery().eq(User::getPhoneNumber, account).one();
        }

        if (one == null) {
            return PoetryResult.fail("用户名或密码错误！");
        }

        // 解密前端传来的AES加密密码
        String decryptedPassword;
        try {
            decryptedPassword = passwordService.decryptFromFrontend(password);
        } catch (Exception e) {
            return PoetryResult.fail("密码格式错误！");
        }

        // 使用新的密码验证服务
        if (!passwordService.matches(decryptedPassword, one.getPassword())) {
            int attempts = recordFailedLoginAttempt(account);
            log.warn("登录失败 - 密码错误: {}, IP: {}, 失败次数: {}", account, clientIp, attempts);
            return PoetryResult.fail("用户名或密码错误！");
        }

        // 登录成功，清除失败记录
        clearFailedLoginAttempts(account);

        // 检查密码是否需要升级（MD5 -> BCrypt）
        if (passwordService.needsUpgrade(one.getPassword())) {
            try {
                String upgradedPassword = passwordService.upgradePassword(decryptedPassword, one.getPassword());
                // 更新数据库中的密码
                lambdaUpdate().eq(User::getId, one.getId())
                        .set(User::getPassword, upgradedPassword)
                        .update();
                // 更新内存中的用户对象
                one.setPassword(upgradedPassword);
                // 记录密码升级统计
                passwordUpgradeService.recordPasswordUpgrade();
            } catch (Exception e) {
                log.error("密码升级失败 - 账号: {}", account, e);
                // 密码升级失败不影响登录，只记录日志
            }
        }

        // 根据用户实际权限判断是否为管理员（而不是依赖前端传递的isAdmin参数）
        boolean isActualAdmin = (one.getUserType() == PoetryEnum.USER_TYPE_ADMIN.getCode() ||
                one.getUserType() == PoetryEnum.USER_TYPE_DEV.getCode());

        // 如果前端请求管理员登录，但用户不是管理员，则拒绝
        if (isAdmin && !isActualAdmin) {
            log.warn("非管理员尝试管理员登录 - 账号: {}, IP: {}", account, clientIp);
            return PoetryResult.fail("请输入管理员账号！");
        }

        // 记录管理员登录
        if (isActualAdmin) {
            recordAdminLoginIp(clientIp);
            // 仅站长(ADMIN)需要清理登录前的访问记录，普通管理员访问正常统计
            if (one.getUserType() == PoetryEnum.USER_TYPE_ADMIN.getCode()) {
                handleOwnerVisitIp(clientIp);
            }
            log.info("管理员登录成功 - 账号: {}, IP: {}", account, clientIp);
        } else {
            log.info("用户登录成功 - 账号: {}, IP: {}", account, clientIp);
        }

        String adminToken = "";
        String userToken = "";

        // 多端登录支持：不再主动清除旧token，允许同时在线
        // 如果需要恢复单点登录，请取消以下注释
        /*
         * // 根据用户实际权限清除旧token（而不是依赖前端传递的isAdmin参数）
         * if (isActualAdmin) {
         * // 清除可能存在的旧管理员token（使用Redis缓存）
         * try {
         * String oldToken = cacheService.getAdminToken(one.getId());
         * if (oldToken != null) {
         * // 清除旧token的会话
         * cacheService.evictUserSession(oldToken);
         * // 清除token映射
         * cacheService.evictAdminToken(one.getId());
         * cacheService.evictTokenInterval(one.getId(), true);
         * }
         * } catch (Exception e) {
         * log.error("清除旧token时发生错误: userId={}", one.getId(), e);
         * }
         * } else {
         * // 清除可能存在的旧用户token（使用Redis缓存）
         * try {
         * String oldToken = cacheService.getUserToken(one.getId());
         * if (oldToken != null) {
         * // 清除旧token的会话
         * cacheService.evictUserSession(oldToken);
         * // 清除token映射
         * cacheService.evictUserToken(one.getId());
         * cacheService.evictTokenInterval(one.getId(), false);
         * }
         * } catch (Exception e) {
         * log.error("清除旧token时发生错误: userId={}", one.getId(), e);
         * }
         * }
         */

        // 根据用户实际权限生成对应的token（而不是依赖前端传递的isAdmin参数）
        if (isActualAdmin && !StringUtils.hasText(adminToken)) {
            adminToken = SecureTokenGenerator.generateAdminToken(one.getId());

            // 使用Redis缓存管理token
            cacheService.cacheUserSession(adminToken, one.getId());
            cacheService.cacheAdminToken(one.getId(), adminToken);
            cacheService.cacheUser(one);
            cacheService.cacheTokenInterval(one.getId(), true);

            // 同时更新管理员缓存（设置为永久缓存）
            cacheService.cacheAdminUser(one);

            // 保持UserCacheManager兼容性
            userCacheManager.cacheUserByToken(adminToken, one);
        } else if (!isActualAdmin && !StringUtils.hasText(userToken)) {
            userToken = SecureTokenGenerator.generateUserToken(one.getId());

            // 使用Redis缓存管理token
            cacheService.cacheUserSession(userToken, one.getId());
            cacheService.cacheUserToken(one.getId(), userToken);
            cacheService.cacheUser(one);
            cacheService.cacheTokenInterval(one.getId(), false);

            // 保持UserCacheManager兼容性
            userCacheManager.cacheUserByToken(userToken, one);
        }

        // 构建返回数据
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(one, userVO);
        userVO.setPassword(null);
        userVO.setUserType(one.getUserType());

        // 根据用户实际权限设置isBoss标志（所有管理员都设置为Boss）
        if (isActualAdmin) {
            userVO.setIsBoss(true);
        }

        // 根据用户实际权限返回对应的token
        if (isActualAdmin) {
            userVO.setAccessToken(adminToken);
        } else {
            userVO.setAccessToken(userToken);
        }
        return PoetryResult.success(userVO);
    }

    @Override
    public PoetryResult exit() {
        try {
            String token = PoetryUtil.getToken();
            Integer userId = PoetryUtil.getUserId();
            String clientIp = PoetryUtil.getIpAddr(PoetryUtil.getRequest());

            // log.info("处理退出登录请求 - token存在: {}, userId: {}, IP: {}",
            // token != null && !token.isEmpty(), userId, clientIp);

            // 即使token或userId为空，也尝试进行部分清理操作
            if (userId != null && token != null) {
                // 判断是管理员还是普通用户token
                boolean isAdminToken = token.contains(CommonConst.ADMIN_ACCESS_TOKEN);

                // 清理用户会话
                cacheService.evictUserSession(token);

                // 清理token映射和间隔检查
                if (isAdminToken) {
                    cacheService.evictAdminToken(userId);
                    cacheService.evictTokenInterval(userId, true);
                } else {
                    cacheService.evictUserToken(userId);
                    cacheService.evictTokenInterval(userId, false);
                }

                // 清理用户信息缓存
                cacheService.evictUser(userId);

                // 如果是普通用户，断开WebSocket连接
                // 注：Spring WebSocket 的会话管理在 ImSessionManager 中处理
                // 用户退出登录时，WebSocket连接会在下次心跳或消息发送时自动关闭

                // 清除UserCacheManager中的用户缓存
                userCacheManager.removeUserByToken(token);
                userCacheManager.removeUserById(userId);

                // log.info("退出登录成功 - 用户ID: {}, IP: {}", userId, clientIp);
            } else {
                // 尝试从请求头获取token进行部分清理
                if (token != null && !token.isEmpty()) {
                    // log.info("userId为空但token存在，尝试清理token相关缓存 - IP: {}", clientIp);
                    try {
                        // 尝试从token获取userId
                        Integer tokenUserId = cacheService.getUserIdFromSession(token);
                        if (tokenUserId != null) {
                            // 清理用户会话
                            cacheService.evictUserSession(token);

                            // 判断是管理员还是普通用户token
                            boolean isAdminToken = token.contains(CommonConst.ADMIN_ACCESS_TOKEN);

                            // 清理token映射和间隔检查
                            if (isAdminToken) {
                                cacheService.evictAdminToken(tokenUserId);
                                cacheService.evictTokenInterval(tokenUserId, true);
                            } else {
                                cacheService.evictUserToken(tokenUserId);
                                cacheService.evictTokenInterval(tokenUserId, false);
                            }

                            // 清理用户信息缓存
                            cacheService.evictUser(tokenUserId);

                            // 清除UserCacheManager中的用户缓存
                            userCacheManager.removeUserByToken(token);
                            userCacheManager.removeUserById(tokenUserId);

                            // log.info("通过token清理用户缓存成功 - 用户ID: {}, IP: {}", tokenUserId, clientIp);
                        } else {
                            // 如果无法从token获取userId，只清理会话
                            cacheService.evictUserSession(token);
                            userCacheManager.removeUserByToken(token);
                            // log.info("仅清理会话缓存 - IP: {}", clientIp);
                        }
                    } catch (Exception e) {
                        log.warn("清理token缓存时发生异常: {}", e.getMessage());
                    }
                } else {
                    log.warn("退出登录时无法获取用户信息和token - IP: {}", clientIp);
                }
            }

            return PoetryResult.success("退出成功");
        } catch (Exception e) {
            log.error("退出登录失败", e);
            // 即使发生异常也返回成功，因为退出操作应该总是成功
            return PoetryResult.success("退出成功");
        }
    }

    @Override
    public PoetryResult<UserVO> regist(UserVO user) {
        // XSS过滤和输入验证
        String filteredUsername = XssFilterUtil.clean(user.getUsername());
        if (!StringUtils.hasText(filteredUsername)) {
            return PoetryResult.fail("用户名不能为空或包含不安全内容！");
        }

        String filteredPhoneNumber = null;
        if (StringUtils.hasText(user.getPhoneNumber())) {
            filteredPhoneNumber = XssFilterUtil.clean(user.getPhoneNumber());
            if (!StringUtils.hasText(filteredPhoneNumber)) {
                return PoetryResult.fail("手机号不能为空或包含不安全内容！");
            }
        }

        String filteredEmail = null;
        if (StringUtils.hasText(user.getEmail())) {
            // 邮箱地址不需要XSS过滤，因为邮箱格式是固定的，不会包含XSS攻击代码
            // 直接使用原始邮箱地址，避免@符号被编码为&#64;
            filteredEmail = user.getEmail().trim();
            if (!StringUtils.hasText(filteredEmail)) {
                return PoetryResult.fail("邮箱不能为空！");
            }

            // 简单验证邮箱格式
            if (!filteredEmail.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")) {
                return PoetryResult.fail("邮箱格式不正确！");
            }
        }

        String regex = "\\d{11}";
        if (filteredUsername.matches(regex)) {
            return PoetryResult.fail("用户名不能为11位数字！");
        }

        if (filteredUsername.contains("@")) {
            return PoetryResult.fail("用户名不能包含@！");
        }

        if (StringUtils.hasText(filteredPhoneNumber) && StringUtils.hasText(filteredEmail)) {
            return PoetryResult.fail("手机号与邮箱只能选择其中一个！");
        }

        if (StringUtils.hasText(filteredPhoneNumber)) {
            String cacheKey = CacheConstants.buildForgetPasswordKey(filteredPhoneNumber, "1");
            Object cachedCode = cacheService.get(cacheKey);
            if (cachedCode == null || !cachedCode.toString().equals(user.getCode())) {
                return PoetryResult.fail("验证码错误！");
            }
            cacheService.deleteKey(cacheKey);
        } else if (StringUtils.hasText(filteredEmail)) {
            String cacheKey = CacheConstants.buildForgetPasswordKey(filteredEmail, "2");
            Object cachedCode = cacheService.get(cacheKey);
            if (cachedCode == null || !cachedCode.toString().equals(user.getCode())) {
                return PoetryResult.fail("验证码错误！");
            }
            cacheService.deleteKey(cacheKey);
        } else {
            return PoetryResult.fail("请输入邮箱或手机号！");
        }

        // 解密前端传来的AES加密密码
        String decryptedPassword;
        try {
            decryptedPassword = passwordService.decryptFromFrontend(user.getPassword());
        } catch (Exception e) {
            return PoetryResult.fail("密码格式错误！");
        }

        // 验证密码是否有效（仅检查非空）
        if (!passwordService.isPasswordValid(decryptedPassword)) {
            return PoetryResult.fail("密码不能为空！");
        }

        Long count = lambdaQuery().eq(User::getUsername, filteredUsername).count();
        if (count != 0) {
            return PoetryResult.fail("用户名重复！");
        }
        if (StringUtils.hasText(filteredPhoneNumber)) {
            Long phoneNumberCount = lambdaQuery().eq(User::getPhoneNumber, filteredPhoneNumber).count();
            if (phoneNumberCount != 0) {
                return PoetryResult.fail("手机号重复！");
            }
        } else if (StringUtils.hasText(filteredEmail)) {
            Long emailCount = lambdaQuery().eq(User::getEmail, filteredEmail).count();
            if (emailCount != 0) {
                return PoetryResult.fail("邮箱重复！");
            }
        }

        User u = new User();
        u.setUsername(filteredUsername);
        u.setPhoneNumber(filteredPhoneNumber);
        u.setEmail(filteredEmail);
        // 新用户直接使用BCrypt加密密码
        u.setPassword(passwordService.encodeBCrypt(decryptedPassword));
        u.setAvatar(PoetryUtil.getRandomAvatar(null));
        save(u);

        User one = lambdaQuery().eq(User::getId, u.getId()).one();

        String userToken = SecureTokenGenerator.generateUserToken(one.getId());

        // 使用Redis缓存替换PoetryCache
        cacheService.cacheUserSession(userToken, one.getId());
        cacheService.cacheUserTokenMapping(one.getId(), userToken);
        cacheService.cacheUser(one);

        // 缓存用户信息到UserCacheManager
        userCacheManager.cacheUserByToken(userToken, one);
        userCacheManager.cacheUserById(one.getId(), one);

        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(one, userVO);
        userVO.setPassword(null);
        userVO.setUserType(one.getUserType());
        userVO.setAccessToken(userToken);

        WeiYan weiYan = new WeiYan();
        weiYan.setUserId(one.getId());
        weiYan.setContent("到此一游");
        weiYan.setType(CommonConst.WEIYAN_TYPE_FRIEND);
        weiYan.setIsPublic(Boolean.TRUE);
        weiYanService.save(weiYan);

        ImChatGroupUser imChatGroupUser = new ImChatGroupUser();
        imChatGroupUser.setGroupId(ImConfigConst.DEFAULT_GROUP_ID);
        imChatGroupUser.setUserId(one.getId());
        imChatGroupUser.setUserStatus(ImConfigConst.GROUP_USER_STATUS_PASS);
        imChatGroupUserMapper.insert(imChatGroupUser);

        // 添加与站长的好友关系（双向）
        User adminUser = PoetryUtil.getAdminUser();
        if (adminUser != null && adminUser.getId() != null) {
            // 新用户添加站长为好友
            ImChatUserFriend imChatUser = new ImChatUserFriend();
            imChatUser.setUserId(one.getId());
            imChatUser.setFriendId(adminUser.getId());
            imChatUser.setRemark("站长");
            imChatUser.setFriendStatus(ImConfigConst.FRIEND_STATUS_PASS);
            imChatUserFriendMapper.insert(imChatUser);

            // 站长添加新用户为好友
            ImChatUserFriend imChatFriend = new ImChatUserFriend();
            imChatFriend.setUserId(adminUser.getId());
            imChatFriend.setFriendId(one.getId());
            imChatFriend.setRemark(one.getUsername()); // 使用新用户的用户名作为备注
            imChatFriend.setFriendStatus(ImConfigConst.FRIEND_STATUS_PASS);
            imChatUserFriendMapper.insert(imChatFriend);

            log.info("新用户 {} 与站长 {} 互相添加好友成功", one.getId(), adminUser.getId());
        } else {
            log.warn("无法获取站长用户信息，新用户 {} 未能自动添加站长为好友", one.getId());
        }

        // 触发插件钩子：新用户注册完成
        pluginHookManager.onUserRegister(Long.valueOf(one.getId()), one.getUsername());

        return PoetryResult.success(userVO);
    }

    @Override
    public PoetryResult<UserVO> updateUserInfo(UserVO user) {
        if (StringUtils.hasText(user.getUsername())) {
            String regex = "\\d{11}";
            if (user.getUsername().matches(regex)) {
                return PoetryResult.fail("用户名不能为11位数字！");
            }

            if (user.getUsername().contains("@")) {
                return PoetryResult.fail("用户名不能包含@！");
            }

            Long count = lambdaQuery().eq(User::getUsername, user.getUsername()).ne(User::getId, PoetryUtil.getUserId())
                    .count();
            if (count != 0) {
                return PoetryResult.fail("用户名重复！");
            }
        }

        User u = new User();
        u.setId(PoetryUtil.getUserId());

        // XSS过滤处理
        if (StringUtils.hasText(user.getUsername())) {
            String filteredUsername = XssFilterUtil.clean(user.getUsername());
            if (!StringUtils.hasText(filteredUsername)) {
                return PoetryResult.fail("用户名内容不合法！");
            }
            u.setUsername(filteredUsername);
        } else {
            u.setUsername(user.getUsername());
        }

        if (StringUtils.hasText(user.getIntroduction())) {
            String filteredIntro = XssFilterUtil.clean(user.getIntroduction());
            if (!StringUtils.hasText(filteredIntro)) {
                return PoetryResult.fail("个人简介内容不合法！");
            }
            u.setIntroduction(filteredIntro);
        } else {
            u.setIntroduction(user.getIntroduction());
        }

        u.setAvatar(user.getAvatar());
        u.setGender(user.getGender());

        updateById(u);
        User one = lambdaQuery().eq(User::getId, u.getId()).one();

        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(one, userVO);
        userVO.setPassword(null);
        userVO.setUserType(one.getUserType());
        return PoetryResult.success(userVO);
    }

    @Override
    public PoetryResult getCode(Integer flag) {
        User user = PoetryUtil.getCurrentUser();
        int i = generateVerificationCode();
        if (flag == 1) {
            if (!StringUtils.hasText(user.getPhoneNumber())) {
                return PoetryResult.fail("请先绑定手机号！");
            }
            return PoetryResult.fail("暂不支持手机号验证码，请使用邮箱验证码");
        } else if (flag == 2) {
            if (!StringUtils.hasText(user.getEmail())) {
                return PoetryResult.fail("请先绑定邮箱！");
            }

            List<String> mail = new ArrayList<>();
            mail.add(user.getEmail());
            String text = getCodeMail(i); // 这里使用已经修改过的getCodeMail方法，会从数据库获取模板
            WebInfo webInfo = cacheService.getCachedWebInfo();

            // 检查邮箱配置是否存在
            if (mailUtil == null || !mailUtil.isEmailConfigured()) {
                return PoetryResult.fail("邮箱服务未配置，请联系管理员在后台设置邮箱配置");
            }

            String countKey = CacheConstants.buildCodeMailCountKey(mail.get(0));
            Object countObj = cacheService.get(countKey);
            Integer count = countObj != null ? (Integer) countObj : 0;

            if (count < CommonConst.CODE_MAIL_COUNT) {
                mailUtil.sendMailMessage(mail, "您有一封来自" + (webInfo == null ? "POETIZE" : webInfo.getWebName()) + "的回执！",
                        text);
                cacheService.set(countKey, count + 1, CommonConst.CODE_EXPIRE);
            } else {
                return PoetryResult.fail("验证码发送次数过多，请明天再试！");
            }

            // 修改密码时会按当前绑定邮箱校验验证码，这里必须用邮箱作为缓存键的一部分。
            String userCodeKey = CacheConstants.buildUserCodeKey(PoetryUtil.getUserId(), user.getEmail(),
                    String.valueOf(flag));
            cacheService.set(userCodeKey, i, 300);
            return PoetryResult.success();
        }
        return PoetryResult.fail("参数异常！");
    }

    @Override
    public PoetryResult getCodeForRegister(String place, Integer flag) {
        String filteredPlace = null;
        if (StringUtils.hasText(place)) {
            if (flag == 2) {
                filteredPlace = place.trim();
                if (!filteredPlace.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")) {
                    return PoetryResult.fail("邮箱格式不正确！");
                }
            } else {
                filteredPlace = XssFilterUtil.clean(place);
                if (!StringUtils.hasText(filteredPlace)) {
                    return PoetryResult.fail("输入内容不合法！");
                }
            }
        }

        int i = generateVerificationCode();
        if (flag == 1) {
            return PoetryResult.fail("暂不支持手机号验证码，请使用邮箱验证码");
        } else if (flag == 2) {
            Long count = lambdaQuery().eq(User::getEmail, filteredPlace).count();
            if (count != 0) {
                return PoetryResult.fail("邮箱重复！");
            }

            List<String> mail = new ArrayList<>();
            mail.add(filteredPlace);
            String text = getCodeMail(i);
            WebInfo webInfo = cacheService.getCachedWebInfo();

            if (mailUtil == null || !mailUtil.isEmailConfigured()) {
                return PoetryResult.fail("邮箱服务未配置，请联系管理员在后台设置邮箱配置");
            }

            String countKey = CacheConstants.buildCodeMailCountKey(mail.get(0));
            Object countObj = cacheService.get(countKey);
            Integer mailCount = countObj != null ? (Integer) countObj : 0;

            if (mailCount < CommonConst.CODE_MAIL_COUNT) {
                mailUtil.sendMailMessage(mail, "您有一封来自" + (webInfo == null ? "POETIZE" : webInfo.getWebName()) + "的回执！",
                        text);
                cacheService.set(countKey, mailCount + 1, CommonConst.CODE_EXPIRE);
            } else {
                return PoetryResult.fail("验证码发送次数过多，请明天再试！");
            }

            String registerCodeKey = CacheConstants.buildForgetPasswordKey(filteredPlace, String.valueOf(flag));
            cacheService.set(registerCodeKey, i, 300);
            return PoetryResult.success();
        }
        return PoetryResult.fail("参数异常！");
    }

    @Override
    public PoetryResult getCodeForBind(String place, Integer flag) {
        // XSS过滤处理（仅对非邮箱地址进行过滤）
        String filteredPlace = null;
        if (StringUtils.hasText(place)) {
            if (flag == 2) {
                // 如果是邮箱地址，不需要XSS过滤，因为邮箱格式是固定的，不会包含XSS攻击代码
                // 直接使用原始邮箱地址，避免@符号被编码为&#64;
                filteredPlace = place.trim();

                // 简单验证邮箱格式
                if (!filteredPlace.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")) {
                    return PoetryResult.fail("邮箱格式不正确！");
                }
            } else {
                // 非邮箱地址进行XSS过滤
                filteredPlace = XssFilterUtil.clean(place);
                if (!StringUtils.hasText(filteredPlace)) {
                    return PoetryResult.fail("输入内容不合法！");
                }
            }
        }

        int i = generateVerificationCode();
        if (flag == 1) {
            return PoetryResult.fail("暂不支持手机号验证码，请使用邮箱验证码");
        } else if (flag == 2) {
            List<String> mail = new ArrayList<>();
            mail.add(filteredPlace);
            String text = getCodeMail(i); // 这里使用已经修改过的getCodeMail方法，会从数据库获取模板
            WebInfo webInfo = cacheService.getCachedWebInfo();

            // 检查邮箱配置是否存在
            if (mailUtil == null || !mailUtil.isEmailConfigured()) {
                return PoetryResult.fail("邮箱服务未配置，请联系管理员在后台设置邮箱配置");
            }

            String countKey = CacheConstants.buildCodeMailCountKey(mail.get(0));
            Object countObj = cacheService.get(countKey);
            Integer count = countObj != null ? (Integer) countObj : 0;

            if (count < CommonConst.CODE_MAIL_COUNT) {
                mailUtil.sendMailMessage(mail, "您有一封来自" + (webInfo == null ? "POETIZE" : webInfo.getWebName()) + "的回执！",
                        text);
                cacheService.set(countKey, count + 1, CommonConst.CODE_EXPIRE);
            } else {
                return PoetryResult.fail("验证码发送次数过多，请明天再试！");
            }

            String userCodeKey = CacheConstants.buildUserCodeKey(PoetryUtil.getUserId(), filteredPlace,
                    String.valueOf(flag));
            cacheService.set(userCodeKey, i, 300);
            return PoetryResult.success();
        }
        return PoetryResult.fail("参数异常！");
    }

    @Override
    public PoetryResult<UserVO> updateSecretInfo(String place, Integer flag, String code, String password) {
        User user = PoetryUtil.getCurrentUser();

        // 判断是否为第三方登录用户
        boolean isThirdPartyUser = StringUtils.hasText(user.getPlatformType());

        // 解密前端传来的AES加密密码
        String decryptedPassword = "";

        // 只有非第三方用户绑定手机号/邮箱或修改密码时才需要解密密码
        if (!isThirdPartyUser || flag == 3) {
            try {
                decryptedPassword = passwordService.decryptFromFrontend(password);
            } catch (Exception e) {
                return PoetryResult.fail("密码格式错误！");
            }
        }

        // 只有非第三方登录用户才需要验证密码（第三方用户没有密码）
        if ((flag == 1 || flag == 2) && !isThirdPartyUser) {
            if (!StringUtils.hasText(decryptedPassword)) {
                return PoetryResult.fail("请输入密码！");
            }
            if (!passwordService.matches(decryptedPassword, user.getPassword())) {
                return PoetryResult.fail("密码错误！");
            }
        }

        if ((flag == 1 || flag == 2 || flag == 3) && !StringUtils.hasText(code)) {
            return PoetryResult.fail("请输入验证码！");
        }

        // XSS过滤处理（仅对非邮箱地址进行过滤）
        String filteredPlace = null;
        if (StringUtils.hasText(place)) {
            if (flag == 2) {
                // 如果是邮箱地址，不需要XSS过滤，因为邮箱格式是固定的，不会包含XSS攻击代码
                // 直接使用原始邮箱地址，避免@符号被编码为&#64;
                filteredPlace = place.trim();

                // 简单验证邮箱格式
                if (!filteredPlace.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")) {
                    return PoetryResult.fail("邮箱格式不正确！");
                }
            } else {
                // 非邮箱地址进行XSS过滤
                filteredPlace = XssFilterUtil.clean(place);
                if (!StringUtils.hasText(filteredPlace)) {
                    return PoetryResult.fail("输入内容不合法！");
                }
            }
        }

        User updateUser = new User();
        updateUser.setId(user.getId());
        if (flag == 1) {
            Long count = lambdaQuery().eq(User::getPhoneNumber, filteredPlace).count();
            if (count != 0) {
                return PoetryResult.fail("手机号重复！");
            }
            String cacheKey = CacheConstants.buildUserCodeKey(PoetryUtil.getUserId(), filteredPlace,
                    String.valueOf(flag));
            Object cachedCode = cacheService.get(cacheKey);
            if (cachedCode != null && cachedCode.toString().equals(code)) {
                cacheService.deleteKey(cacheKey);
                updateUser.setPhoneNumber(filteredPlace);
            } else {
                return PoetryResult.fail("验证码错误！");
            }

        } else if (flag == 2) {
            Long count = lambdaQuery().eq(User::getEmail, filteredPlace).count();
            if (count != 0) {
                return PoetryResult.fail("邮箱重复！");
            }
            String cacheKey = CacheConstants.buildUserCodeKey(PoetryUtil.getUserId(), filteredPlace,
                    String.valueOf(flag));
            Object cachedCode = cacheService.get(cacheKey);
            if (cachedCode != null && cachedCode.toString().equals(code)) {
                cacheService.deleteKey(cacheKey);
                updateUser.setEmail(filteredPlace);
            } else {
                return PoetryResult.fail("验证码错误！");
            }
        } else if (flag == 3) {
            // flag == 3 表示修改密码，place是旧密码，password是新密码
            String oldPassword;
            try {
                oldPassword = passwordService.decryptFromFrontend(place);
            } catch (Exception e) {
                return PoetryResult.fail("旧密码格式错误！");
            }

            // 验证旧密码
            if (!passwordService.matches(oldPassword, user.getPassword())) {
                return PoetryResult.fail("旧密码错误！");
            }

            // 验证新密码是否有效（仅检查非空）
            if (!passwordService.isPasswordValid(decryptedPassword)) {
                return PoetryResult.fail("新密码不能为空！");
            }

            // 验证收到的邮箱验证码
            String cacheKey = CacheConstants.buildUserCodeKey(PoetryUtil.getUserId(), user.getEmail(),
                    String.valueOf(2));
            Object cachedCode = cacheService.get(cacheKey);
            if (cachedCode != null && cachedCode.toString().equals(code)) {
                cacheService.deleteKey(cacheKey);
            } else {
                return PoetryResult.fail("验证码错误！");
            }

            // 使用BCrypt加密新密码
            updateUser.setPassword(passwordService.encodeBCrypt(decryptedPassword));
            log.info("修改密码成功 - 用户ID: {}", user.getId());
        }
        updateById(updateUser);

        // 安全加固：修改密码后，强制下线该用户的所有设备
        if (flag == 3) {
            cacheService.evictAllUserTokens(user.getId());
            cacheService.evictUser(user.getId());
            log.info("修改密码成功，已强制下线用户的所有Session - 用户ID: {}", user.getId());
        }

        User one = lambdaQuery().eq(User::getId, user.getId()).one();

        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(one, userVO);
        userVO.setPassword(null);
        return PoetryResult.success(userVO);
    }

    @Override
    public PoetryResult getCodeForForgetPassword(String username, String place, Integer flag) {
        // XSS过滤处理（仅对非邮箱地址进行过滤）
        String filteredPlace = null;
        if (StringUtils.hasText(place)) {
            if (flag == 2) {
                // 如果是邮箱地址，不需要XSS过滤，因为邮箱格式是固定的，不会包含XSS攻击代码
                // 直接使用原始邮箱地址，避免@符号被编码为&#64;
                filteredPlace = place.trim();

                // 简单验证邮箱格式
                if (!filteredPlace.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")) {
                    return PoetryResult.fail("邮箱格式不正确！");
                }
            } else {
                // 非邮箱地址进行XSS过滤
                filteredPlace = XssFilterUtil.clean(place);
                if (!StringUtils.hasText(filteredPlace)) {
                    return PoetryResult.fail("输入内容不合法！");
                }
            }
        }

        int i = generateVerificationCode();
        if (flag == 1) {
            return PoetryResult.fail("暂不支持手机号验证码，请使用邮箱验证码");
        } else if (flag == 2) {

            // 强化双重校验：确保邮箱和用户名属于同一已注册账户
            Long count = lambdaQuery().eq(User::getEmail, filteredPlace).eq(User::getUsername, username).count();
            if (count == 0) {
                return PoetryResult.fail("账户信息不匹配或未注册！");
            }

            List<String> mail = new ArrayList<>();
            mail.add(filteredPlace);
            String text = getCodeMail(i);
            WebInfo webInfo = cacheService.getCachedWebInfo();

            // 检查邮箱配置是否存在
            if (mailUtil == null || !mailUtil.isEmailConfigured()) {
                return PoetryResult.fail("邮箱服务未配置，请联系管理员在后台设置邮箱配置");
            }

            String countKey = CacheConstants.buildCodeMailCountKey(mail.get(0));
            Object countObj = cacheService.get(countKey);
            Integer mailCount = countObj != null ? (Integer) countObj : 0;

            if (mailCount < CommonConst.CODE_MAIL_COUNT) {
                mailUtil.sendMailMessage(mail, "您有一封来自" + (webInfo == null ? "POETIZE" : webInfo.getWebName()) + "的回执！",
                        text);
                cacheService.set(countKey, mailCount + 1, CommonConst.CODE_EXPIRE);
            } else {
                return PoetryResult.fail("验证码发送次数过多，请明天再试！");
            }

            String forgetPasswordKey = CacheConstants.buildForgetPasswordKey(filteredPlace, String.valueOf(flag));
            cacheService.set(forgetPasswordKey, i, 300);
            return PoetryResult.success();
        }
        return PoetryResult.fail("参数异常！");
    }

    private int generateVerificationCode() {
        return SECURE_RANDOM.nextInt(900000) + 100000;
    }

    @Override
    public PoetryResult updateForForgetPassword(String username, String place, Integer flag, String code,
            String password) {
        // XSS过滤处理（仅对非邮箱地址进行过滤）
        String filteredPlace = null;
        if (StringUtils.hasText(place)) {
            if (flag == 2) {
                // 如果是邮箱地址，不需要XSS过滤，因为邮箱格式是固定的，不会包含XSS攻击代码
                // 直接使用原始邮箱地址，避免@符号被编码为&#64;
                filteredPlace = place.trim();

                // 简单验证邮箱格式
                if (!filteredPlace.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")) {
                    return PoetryResult.fail("邮箱格式不正确！");
                }
            } else {
                // 非邮箱地址进行XSS过滤
                filteredPlace = XssFilterUtil.clean(place);
                if (!StringUtils.hasText(filteredPlace)) {
                    return PoetryResult.fail("输入内容不合法！");
                }
            }
        }

        // 解密前端传来的AES加密密码
        String decryptedPassword;
        try {
            decryptedPassword = passwordService.decryptFromFrontend(password);
        } catch (Exception e) {
            return PoetryResult.fail("密码格式错误！");
        }

        // 验证密码是否有效（仅检查非空）
        if (!passwordService.isPasswordValid(decryptedPassword)) {
            return PoetryResult.fail("密码不能为空！");
        }

        String forgetPasswordKey = CacheConstants.buildForgetPasswordKey(filteredPlace, String.valueOf(flag));
        Object cachedCode = cacheService.get(forgetPasswordKey);
        if (cachedCode == null || !cachedCode.toString().equals(code)) {
            return PoetryResult.fail("验证码错误！");
        }

        cacheService.deleteKey(forgetPasswordKey);

        // 使用BCrypt加密新密码
        String encodedPassword = passwordService.encodeBCrypt(decryptedPassword);

        if (flag == 1) {
            User user = lambdaQuery().eq(User::getPhoneNumber, filteredPlace).eq(User::getUsername, username).one();
            if (user == null) {
                return PoetryResult.fail("该手机号与用户名未匹配或未绑定！");
            }

            if (!user.getUserStatus()) {
                return PoetryResult.fail("账号被冻结！");
            }

            lambdaUpdate().eq(User::getPhoneNumber, filteredPlace).set(User::getPassword, encodedPassword).update();
            cacheService.evictUser(user.getId());
            cacheService.evictAllUserTokens(user.getId()); // 清理所有token，强制重新登录
            log.info("通过手机号重置密码成功 - 用户ID: {}", user.getId());
        } else if (flag == 2) {
            User user = lambdaQuery().eq(User::getEmail, filteredPlace).eq(User::getUsername, username).one();
            if (user == null) {
                return PoetryResult.fail("该邮箱与用户名未匹配或未绑定！");
            }

            if (!user.getUserStatus()) {
                return PoetryResult.fail("账号被冻结！");
            }

            lambdaUpdate().eq(User::getEmail, filteredPlace).set(User::getPassword, encodedPassword).update();
            cacheService.evictUser(user.getId());
            cacheService.evictAllUserTokens(user.getId()); // 清理所有token，强制重新登录
            log.info("通过邮箱重置密码成功 - 用户ID: {}", user.getId());
        }

        return PoetryResult.success();
    }

    @Override
    public PoetryResult<Page<UserVO>> listUser(BaseRequestVO baseRequestVO) {
        LambdaQueryChainWrapper<User> lambdaQuery = lambdaQuery();

        if (baseRequestVO.getUserStatus() != null) {
            lambdaQuery.eq(User::getUserStatus, baseRequestVO.getUserStatus());
        }

        if (baseRequestVO.getUserType() != null) {
            lambdaQuery.eq(User::getUserType, baseRequestVO.getUserType());
        }

        // 根据是否为第三方登录用户进行筛选
        if (baseRequestVO.getIsThirdPartyUser() != null) {
            if (baseRequestVO.getIsThirdPartyUser()) {
                // 筛选第三方登录用户：platform_type不为空
                lambdaQuery.isNotNull(User::getPlatformType)
                        .ne(User::getPlatformType, "");
            } else {
                // 筛选普通注册用户：platform_type为空或空字符串
                lambdaQuery.and(wrapper -> wrapper.isNull(User::getPlatformType)
                        .or()
                        .eq(User::getPlatformType, ""));
            }
        }

        if (StringUtils.hasText(baseRequestVO.getSearchKey())) {
            lambdaQuery.and(lq -> lq.like(User::getUsername, baseRequestVO.getSearchKey())
                    .or()
                    .like(User::getPhoneNumber, baseRequestVO.getSearchKey())
                    .or()
                    .like(User::getEmail, baseRequestVO.getSearchKey()));
        }

        Page<User> page = new Page<>(baseRequestVO.getCurrent(), baseRequestVO.getSize());
        Page<User> resultPage = lambdaQuery.orderByDesc(User::getCreateTime).page(page);

        // 转换为UserVO并设置isThirdPartyUser字段
        List<UserVO> userVOList = new ArrayList<>();
        List<User> records = resultPage.getRecords();
        if (!CollectionUtils.isEmpty(records)) {
            userVOList = records.stream().map(user -> {
                UserVO userVO = new UserVO();
                BeanUtils.copyProperties(user, userVO);

                // 显式设置关键字段，确保数据正确传输
                userVO.setUserStatus(user.getUserStatus());
                userVO.setAdmire(user.getAdmire());
                userVO.setUserType(user.getUserType());

                // 设置是否为第三方登录用户
                userVO.setIsThirdPartyUser(user.getPlatformType() != null && !user.getPlatformType().trim().isEmpty());

                // 清除敏感信息
                userVO.setPassword(null);
                userVO.setOpenId(null);

                return userVO;
            }).collect(Collectors.toList());
        }

        // 创建UserVO的分页结果
        Page<UserVO> userVOPage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        userVOPage.setRecords(userVOList);

        return PoetryResult.success(userVOPage);
    }

    @Override
    public PoetryResult<List<UserVO>> getUserByUsername(String username) {
        List<User> users = lambdaQuery()
                .select(User::getId, User::getUsername, User::getAvatar, User::getGender, User::getIntroduction)
                .like(User::getUsername, username).last("limit 5").list();
        List<UserVO> userVOS = users.stream().map(u -> {
            UserVO userVO = new UserVO();
            userVO.setId(u.getId());
            userVO.setUsername(u.getUsername());
            userVO.setAvatar(u.getAvatar());
            userVO.setIntroduction(u.getIntroduction());
            userVO.setGender(u.getGender());
            return userVO;
        }).collect(Collectors.toList());
        return PoetryResult.success(userVOS);
    }

    @Override
    public PoetryResult<UserVO> token(String userToken) {
        if (StringUtils.hasText(userToken)) {
            userToken = CryptoUtil.decrypt(userToken);
        } else {
            userToken = PoetryUtil.getTokenWithoutBearer();
        }

        if (!StringUtils.hasText(userToken)) {
            throw new PoetryRuntimeException("登录已过期，请重新登陆！");
        }

        // 首先验证token的安全性和有效性
        if (!TokenValidationUtil.isValidToken(userToken)) {
            log.warn("Token验证失败");
            throw new PoetryRuntimeException("Token无效，请重新登陆！");
        }

        // 使用多级缓存策略获取用户信息
        User user = null;

        // 优先从UserCacheManager获取（已重构为Redis缓存）
        user = userCacheManager.getUserByToken(userToken);

        // 备用方案：直接从Redis缓存获取
        if (user == null) {
            Integer userId = cacheService.getUserIdFromSession(userToken);
            if (userId != null) {
                user = cacheService.getCachedUser(userId);
            }
        }

        // 如果缓存中都没有，说明token可能已过期或用户已退出登录
        if (user == null) {
            // 检查token是否在有效期内且格式正确
            Integer userIdFromToken = TokenValidationUtil.extractUserId(userToken);
            if (userIdFromToken != null) {
                throw new PoetryRuntimeException("登录已过期，请重新登陆！");
            } else {
                // token格式无效
                throw new PoetryRuntimeException("Token无效，请重新登陆！");
            }
        }

        // 根据用户实际权限判断是否为管理员
        boolean isActualAdmin = (user.getUserType() == PoetryEnum.USER_TYPE_ADMIN.getCode() ||
                user.getUserType() == PoetryEnum.USER_TYPE_DEV.getCode());

        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        userVO.setPassword(null);
        userVO.setUserType(user.getUserType());

        // 根据用户实际权限设置isBoss标志（与登录逻辑保持一致）
        if (isActualAdmin) {
            userVO.setIsBoss(true);
        } else {
            userVO.setIsBoss(false);
        }

        return PoetryResult.success(userVO);
    }

    @Override
    public PoetryResult<UserVO> subscribe(Integer labelId, Boolean flag) {
        UserVO userVO = null;
        User one = lambdaQuery().eq(User::getId, PoetryUtil.getUserId()).one();
        List<Integer> sub = JsonUtils.parseArray(one.getSubscribe(), Integer.class);
        if (sub == null)
            sub = new ArrayList<>();
        if (flag) {
            if (!sub.contains(labelId)) {
                sub.add(labelId);
                User user = new User();
                user.setId(one.getId());
                user.setSubscribe(JsonUtils.toJsonString(sub));
                updateById(user);
                cacheService.evictSortArticleList();
                cacheService.evictSubscribeCount();

                userVO = new UserVO();
                BeanUtils.copyProperties(one, userVO);
                userVO.setPassword(null);
                userVO.setSubscribe(user.getSubscribe());
            }
        } else {
            if (sub.contains(labelId)) {
                sub.remove(labelId);
                User user = new User();
                user.setId(one.getId());
                user.setSubscribe(JsonUtils.toJsonString(sub));
                updateById(user);
                cacheService.evictSortArticleList();
                cacheService.evictSubscribeCount();

                userVO = new UserVO();
                BeanUtils.copyProperties(one, userVO);
                userVO.setPassword(null);
                userVO.setSubscribe(user.getSubscribe());
            }
        }
        return PoetryResult.success(userVO);
    }

    @Override
    public PoetryResult<UserVO> thirdLogin(String provider, String uid, String username, String email, String avatar) {
        if (!StringUtils.hasText(provider) || !StringUtils.hasText(uid)) {
            return PoetryResult.fail("第三方登录信息不完整");
        }

        // XSS过滤和输入验证
        String filteredProvider = XssFilterUtil.clean(provider);
        String filteredUid = XssFilterUtil.clean(uid);
        String filteredUsername = null;
        if (StringUtils.hasText(username)) {
            filteredUsername = XssFilterUtil.clean(username);
            if (!StringUtils.hasText(filteredUsername)) {
                return PoetryResult.fail("用户名不能为空或包含不安全内容！");
            }
        }
        String filteredEmail = null;
        if (StringUtils.hasText(email)) {
            // 邮箱地址不需要XSS过滤，因为邮箱格式是固定的，不会包含XSS攻击代码
            // 直接使用原始邮箱地址，避免@符号被编码为&#64;
            filteredEmail = email.trim();
            if (!StringUtils.hasText(filteredEmail)) {
                return PoetryResult.fail("邮箱不能为空！");
            }

            // 简单验证邮箱格式
            if (!filteredEmail.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")) {
                return PoetryResult.fail("邮箱格式不正确！");
            }
        }
        String filteredAvatar = null;
        if (StringUtils.hasText(avatar)) {
            filteredAvatar = XssFilterUtil.clean(avatar);
            if (!StringUtils.hasText(filteredAvatar)) {
                return PoetryResult.fail("头像不能为空或包含不安全内容！");
            }
        }

        User existUser = lambdaQuery()
                .eq(User::getPlatformType, filteredProvider)
                .eq(User::getUid, filteredUid)
                .one();

        if (existUser == null) {
            // 新用户注册逻辑
            String finalUsername = filteredUsername;
            if (!StringUtils.hasText(finalUsername)) {
                finalUsername = filteredProvider + "_user_" + System.currentTimeMillis();
            }

            int count = 0;
            String uniqueUsername = finalUsername;
            while (lambdaQuery().eq(User::getUsername, uniqueUsername).count() > 0) {
                uniqueUsername = finalUsername + "_" + (++count);
            }

            User newUser = new User();
            newUser.setUsername(uniqueUsername);
            newUser.setPlatformType(filteredProvider);
            newUser.setUid(filteredUid);
            newUser.setEmail(filteredEmail);
            newUser.setAvatar(filteredAvatar);
            newUser.setUserStatus(true);
            newUser.setUserType(PoetryEnum.USER_TYPE_USER.getCode());
            newUser.setGender(PoetryEnum.USER_GENDER_NONE.getCode());

            save(newUser);

            // 添加默认群聊成员
            ImChatGroupUser imChatGroupUser = new ImChatGroupUser();
            imChatGroupUser.setGroupId(ImConfigConst.DEFAULT_GROUP_ID);
            imChatGroupUser.setUserId(newUser.getId());
            imChatGroupUser.setAdminFlag(false);
            imChatGroupUser.setUserStatus(ImConfigConst.GROUP_USER_STATUS_PASS);
            imChatGroupUserMapper.insert(imChatGroupUser);

            // 添加与站长的好友关系（双向）
            User adminUser = PoetryUtil.getAdminUser();
            if (adminUser != null && adminUser.getId() != null) {
                // 新用户添加站长为好友
                ImChatUserFriend imChatUser = new ImChatUserFriend();
                imChatUser.setUserId(newUser.getId());
                imChatUser.setFriendId(adminUser.getId());
                imChatUser.setRemark("站长");
                imChatUser.setFriendStatus(ImConfigConst.FRIEND_STATUS_PASS);
                imChatUserFriendMapper.insert(imChatUser);

                // 站长添加新用户为好友
                ImChatUserFriend imChatFriend = new ImChatUserFriend();
                imChatFriend.setUserId(adminUser.getId());
                imChatFriend.setFriendId(newUser.getId());
                imChatFriend.setRemark(newUser.getUsername()); // 使用新用户的用户名作为备注
                imChatFriend.setFriendStatus(ImConfigConst.FRIEND_STATUS_PASS);
                imChatUserFriendMapper.insert(imChatFriend);

                log.info("第三方登录新用户 {} 与站长 {} 互相添加好友成功", newUser.getId(), adminUser.getId());
            } else {
                log.warn("无法获取站长用户信息，第三方登录新用户 {} 未能自动添加站长为好友", newUser.getId());
            }

            // 添加默认随笔"到此一游"
            WeiYan weiYan = new WeiYan();
            weiYan.setUserId(newUser.getId());
            weiYan.setContent("到此一游");
            weiYan.setType(CommonConst.WEIYAN_TYPE_FRIEND);
            weiYan.setIsPublic(Boolean.TRUE);
            weiYanService.save(weiYan);

            existUser = newUser;
            log.info("第三方账号注册成功 - 平台: {}, 用户ID: {}", provider, newUser.getId());
            pluginHookManager.onUserRegister(Long.valueOf(newUser.getId()), newUser.getUsername());
        } else {
            // 🔧 已存在用户的邮箱更新逻辑
            boolean userHasEmailInDB = StringUtils.hasText(existUser.getEmail());
            boolean thirdPartyProvidedEmail = StringUtils.hasText(filteredEmail);

            // 如果数据库中没有邮箱，但第三方平台提供了邮箱，则更新数据库
            if (!userHasEmailInDB && thirdPartyProvidedEmail) {

                User updateUser = new User();
                updateUser.setId(existUser.getId());
                updateUser.setEmail(filteredEmail);
                updateById(updateUser);

                // 更新内存中的用户对象
                existUser.setEmail(filteredEmail);
            }

            // 🔧 头像处理策略：保持用户自定义头像不变
            // 对于已存在的用户，不自动更新头像，避免覆盖用户在个人中心自定义的头像
        }

        // 根据用户实际权限判断是否为管理员
        boolean isActualAdmin = (existUser.getUserType() == PoetryEnum.USER_TYPE_ADMIN.getCode() ||
                existUser.getUserType() == PoetryEnum.USER_TYPE_DEV.getCode());
        // 仅站长(ADMIN)需要清理登录前的访问记录
        if (existUser.getUserType() == PoetryEnum.USER_TYPE_ADMIN.getCode()) {
            handleOwnerVisitIp(PoetryUtil.getIpAddr(PoetryUtil.getRequest()));
        }

        String adminToken = "";
        String userToken = "";

        // 根据用户实际权限生成对应的token
        if (isActualAdmin) {
            // 管理员用户：生成管理员token
            adminToken = cacheService.getAdminToken(existUser.getId());

            if (!StringUtils.hasText(adminToken)) {
                adminToken = SecureTokenGenerator.generateAdminToken(existUser.getId());

                // 使用Redis缓存管理管理员token
                cacheService.cacheUserSession(adminToken, existUser.getId());
                cacheService.cacheAdminToken(existUser.getId(), adminToken);
                cacheService.cacheUser(existUser);
                cacheService.cacheTokenInterval(existUser.getId(), true);

                // 保持UserCacheManager兼容性
                userCacheManager.cacheUserByToken(adminToken, existUser);

            }
        } else {
            // 普通用户：生成用户token
            userToken = cacheService.getUserToken(existUser.getId());

            if (!StringUtils.hasText(userToken)) {
                userToken = SecureTokenGenerator.generateUserToken(existUser.getId());

                // 使用Redis缓存管理用户token
                cacheService.cacheUserSession(userToken, existUser.getId());
                cacheService.cacheUserToken(existUser.getId(), userToken);
                cacheService.cacheUser(existUser);
                cacheService.cacheTokenInterval(existUser.getId(), false);

                // 保持UserCacheManager兼容性
                userCacheManager.cacheUserByToken(userToken, existUser);

            }
        }

        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(existUser, userVO);
        userVO.setPassword(null);
        userVO.setUserType(existUser.getUserType());

        // 根据用户实际权限设置isBoss标志（所有管理员都设置为Boss）
        if (isActualAdmin) {
            userVO.setIsBoss(true);
        } else {
            userVO.setIsBoss(false);
        }

        // 根据用户实际权限返回对应的token
        if (isActualAdmin) {
            userVO.setAccessToken(adminToken);
        } else {
            userVO.setAccessToken(userToken);
        }

        return PoetryResult.success(userVO);
    }

    private String getCodeMail(int i) {
        WebInfo webInfo = cacheService.getCachedWebInfo();
        String webName = (webInfo == null ? "POETIZE" : webInfo.getWebName());

        // 从数据库获取验证码模板
        String template = sysConfigService.getConfigValueByKey("user.code.format");
        if (template == null || template.trim().isEmpty()) {
            // 如果数据库中没有配置，使用默认模板
            template = "【POETIZE】%s为本次验证的验证码，请在5分钟内完成验证。为保证账号安全，请勿泄漏此验证码。";
            log.warn("数据库中未找到验证码模板配置，使用默认模板");
        }

        return String.format(mailUtil.getMailText(),
                webName,
                String.format(MailUtil.imMail, PoetryUtil.getAdminUser().getUsername()),
                PoetryUtil.getAdminUser().getUsername(),
                String.format(template, i),
                "",
                webName);
    }

    @Override
    public PoetryResult getBindablePlatforms() {
        try {
            User currentUser = PoetryUtil.getCurrentUser();

            // 只有普通注册用户才能绑定第三方账号
            if (currentUser.getPlatformType() != null && !currentUser.getPlatformType().trim().isEmpty()) {
                return PoetryResult.fail("第三方登录用户不能绑定其他账号");
            }

            // 获取已启用且全局启用的第三方平台配置
            List<ThirdPartyOauthConfig> enabledConfigs = thirdPartyOauthConfigService.getActiveConfigs();

            // 转换为前端需要的格式
            List<Map<String, Object>> platforms = enabledConfigs.stream()
                    .map(config -> {
                        Map<String, Object> platform = new HashMap<>();
                        platform.put("platformType", config.getPlatformType());
                        platform.put("platformName", getPlatformDisplayName(config.getPlatformType()));
                        platform.put("iconUrl", getPlatformIconUrl(config.getPlatformType()));
                        return platform;
                    })
                    .collect(Collectors.toList());

            return PoetryResult.success(platforms);
        } catch (Exception e) {
            log.error("获取可绑定平台列表失败", e);
            return PoetryResult.fail("获取平台列表失败");
        }
    }

    @Override
    public PoetryResult getAccountBindingStatus() {
        try {
            User currentUser = PoetryUtil.getCurrentUser();

            Map<String, Object> status = new HashMap<>();
            status.put("isThirdPartyUser",
                    currentUser.getPlatformType() != null && !currentUser.getPlatformType().trim().isEmpty());
            status.put("boundPlatform", currentUser.getPlatformType());
            status.put("boundPlatformName", getPlatformDisplayName(currentUser.getPlatformType()));
            status.put("canBind",
                    currentUser.getPlatformType() == null || currentUser.getPlatformType().trim().isEmpty());

            return PoetryResult.success(status);
        } catch (Exception e) {
            log.error("获取账号绑定状态失败", e);
            return PoetryResult.fail("获取绑定状态失败");
        }
    }

    @Override
    public PoetryResult getOAuthAuthUrl(String platformType) {
        try {
            User currentUser = PoetryUtil.getCurrentUser();

            // 检查是否为普通注册用户
            if (currentUser.getPlatformType() != null && !currentUser.getPlatformType().trim().isEmpty()) {
                return PoetryResult.fail("第三方登录用户不能绑定其他账号");
            }

            // 检查平台是否已配置
            if (!oAuthClientService.isPlatformConfigured(platformType)) {
                return PoetryResult.fail("平台未配置或未启用");
            }

            // 生成state参数（简单随机字符串，绑定功能已移除）
            String state = java.util.UUID.randomUUID().toString();

            // 构建授权URL
            String authUrl = oAuthClientService.buildAuthUrl(platformType, state);

            Map<String, Object> result = new HashMap<>();
            result.put("authUrl", authUrl);
            result.put("platformType", platformType);
            result.put("state", state);

            return PoetryResult.success(result);
        } catch (Exception e) {
            log.error("获取OAuth授权URL失败", e);
            return PoetryResult.fail("获取授权URL失败：" + e.getMessage());
        }
    }

    @Override
    public PoetryResult bindThirdPartyAccount(String platformType, String code, String state) {
        try {
            log.info("开始绑定第三方账号: platformType={}, code={}, state={}", platformType, code, state);

            // 绑定功能已移除，直接返回错误
            log.warn("绑定功能已移除");
            return PoetryResult.fail("绑定功能暂不可用");

        } catch (Exception e) {
            log.error("绑定第三方账号失败", e);
            return PoetryResult.fail("绑定失败：" + e.getMessage());
        }
    }

    @Override
    public PoetryResult unbindThirdPartyAccount() {
        try {
            User currentUser = PoetryUtil.getCurrentUser();

            // 检查是否已绑定第三方账号
            if (currentUser.getPlatformType() == null || currentUser.getPlatformType().trim().isEmpty()) {
                return PoetryResult.fail("您还没有绑定第三方账号");
            }

            // 解绑第三方账号
            currentUser.setPlatformType(null);
            currentUser.setUid(null);

            boolean success = updateById(currentUser);
            if (success) {
                return PoetryResult.success("解绑成功");
            } else {
                return PoetryResult.fail("解绑失败");
            }
        } catch (Exception e) {
            log.error("解绑第三方账号失败", e);
            return PoetryResult.fail("解绑失败");
        }
    }

    /**
     * 获取平台显示名称
     */
    private String getPlatformDisplayName(String platformType) {
        if (platformType == null) {
            return null;
        }
        switch (platformType.toLowerCase()) {
            case "github":
                return "GitHub";
            case "google":
                return "Google";
            case "x":
            case "twitter":
                return "Twitter/X";
            case "yandex":
                return "Yandex";
            case "gitee":
                return "Gitee";
            case "qq":
                return "QQ";
            case "baidu":
                return "Baidu";
            default:
                return platformType;
        }
    }

    /**
     * 获取平台图标URL
     */
    private String getPlatformIconUrl(String platformType) {
        if (platformType == null) {
            return null;
        }
        return "/static/svg/" + platformType.toLowerCase() + ".svg";
    }
}
