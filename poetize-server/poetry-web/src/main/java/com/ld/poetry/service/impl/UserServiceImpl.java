package com.ld.poetry.service.impl;

import cn.hutool.crypto.SecureUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.dao.UserMapper;
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
import com.ld.poetry.im.websocket.TioUtil;
import com.ld.poetry.im.websocket.TioWebsocketStarter;
import com.ld.poetry.service.UserService;
import com.ld.poetry.service.WeiYanService;
import com.ld.poetry.utils.*;
import com.ld.poetry.utils.cache.PoetryCache;
import com.ld.poetry.utils.mail.MailUtil;
import com.ld.poetry.vo.BaseRequestVO;
import com.ld.poetry.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.tio.core.Tio;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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

    @Autowired
    private WeiYanService weiYanService;

    @Autowired
    private ImChatGroupUserMapper imChatGroupUserMapper;

    @Autowired
    private ImChatUserFriendMapper imChatUserFriendMapper;

    @Autowired
    private MailUtil mailUtil;

    @Value("${user.code.format}")
    private String codeFormat;

    /**
     * 检查IP是否在管理员IP白名单中
     * @param ip 客户端IP
     * @return 是否在白名单中
     */
    private boolean isIpInAdminWhitelist(String ip) {
        Set<String> whitelistedIps = (Set<String>) PoetryCache.get(CommonConst.ADMIN_IP_WHITELIST);
        if (whitelistedIps == null || whitelistedIps.isEmpty()) {
            // 初始化时没有白名单，允许第一次登录设置
            return true;
        }
        return whitelistedIps.contains(ip);
    }

    /**
     * 记录管理员登录IP
     * @param ip 客户端IP
     */
    private void recordAdminLoginIp(String ip) {
        Set<String> whitelistedIps = (Set<String>) PoetryCache.get(CommonConst.ADMIN_IP_WHITELIST);
        if (whitelistedIps == null) {
            whitelistedIps = new HashSet<>();
        }
        whitelistedIps.add(ip);
        PoetryCache.put(CommonConst.ADMIN_IP_WHITELIST, whitelistedIps);
    }

    /**
     * 检查账号是否被锁定
     * @param account 账号
     * @return 是否被锁定
     */
    private boolean isAccountLocked(String account) {
        String lockKey = "login_lock_" + account;
        return PoetryCache.get(lockKey) != null;
    }

    /**
     * 记录失败登录尝试
     * @param account 账号
     * @return 当前尝试次数
     */
    private int recordFailedLoginAttempt(String account) {
        String attemptKey = "login_attempt_" + account;
        Integer attempts = (Integer) PoetryCache.get(attemptKey);
        if (attempts == null) {
            attempts = 1;
        } else {
            attempts++;
        }
        
        // 设置失败尝试记录，过期时间1小时
        PoetryCache.put(attemptKey, attempts, 3600);
        
        // 如果失败次数超过阈值，锁定账号
        if (attempts >= CommonConst.MAX_LOGIN_ATTEMPTS) {
            String lockKey = "login_lock_" + account;
            PoetryCache.put(lockKey, true, CommonConst.LOGIN_LOCKOUT_TIME);
            log.warn("账号 {} 因多次登录失败被锁定 {} 秒", account, CommonConst.LOGIN_LOCKOUT_TIME);
        }
        
        return attempts;
    }

    /**
     * 清除失败登录尝试记录
     * @param account 账号
     */
    private void clearFailedLoginAttempts(String account) {
        String attemptKey = "login_attempt_" + account;
        PoetryCache.remove(attemptKey);
    }

    @Override
    public PoetryResult<UserVO> login(String account, String password, Boolean isAdmin) {
        // 获取客户端IP
        String clientIp = PoetryUtil.getIpAddr(PoetryUtil.getRequest());
        log.info("用户登录尝试 - 用户名: {}, IP: {}, 是否管理员: {}", account, clientIp, isAdmin);

        // 检查账号是否被锁定
        String lockKey = "login_lock_" + account;
        if (PoetryCache.get(lockKey) != null) {
            log.warn("账号已被锁定 - 用户名: {}, IP: {}", account, clientIp);
            return PoetryResult.fail("账号已被锁定，请稍后再试");
        }

        // 验证用户名和密码
        User one = lambdaQuery().eq(User::getUsername, account).one();
        if (one == null) {
            log.warn("登录失败 - 用户不存在: {}, IP: {}", account, clientIp);
            return PoetryResult.fail("用户名或密码错误！");
        }

        // 解密前端传来的AES加密密码
        String decryptedPassword = new String(SecureUtil.aes(CommonConst.CRYPOTJS_KEY.getBytes(StandardCharsets.UTF_8)).decrypt(password));
        
        if (!one.getPassword().equals(DigestUtils.md5DigestAsHex(decryptedPassword.getBytes()))) {
            int attempts = recordFailedLoginAttempt(account);
            log.warn("登录失败 - 密码错误: {}, IP: {}, 失败次数: {}", account, clientIp, attempts);
            return PoetryResult.fail("用户名或密码错误！");
        }

        // 登录成功，清除失败记录
        clearFailedLoginAttempts(account);
        log.info("用户登录成功 - 用户名: {}, IP: {}", account, clientIp);

        // 检查管理员权限
        if (isAdmin && (one.getUserType() == PoetryEnum.USER_TYPE_ADMIN.getCode() || one.getUserType() == PoetryEnum.USER_TYPE_DEV.getCode())) {
            recordAdminLoginIp(clientIp);
            log.info("管理员登录成功 - 用户名: {}, IP: {}", account, clientIp);
        }

        String adminToken = "";
        String userToken = "";

        // 清除旧token
        if (isAdmin) {
            if (one.getUserType() != PoetryEnum.USER_TYPE_ADMIN.getCode() && one.getUserType() != PoetryEnum.USER_TYPE_DEV.getCode()) {
                log.warn("非管理员尝试管理员登录 - 用户名: {}, IP: {}", account, clientIp);
                return PoetryResult.fail("请输入管理员账号！");
            }
            // 清除可能存在的旧token
            if (PoetryCache.get(CommonConst.ADMIN_TOKEN + one.getId()) != null) {
                String oldToken = (String) PoetryCache.get(CommonConst.ADMIN_TOKEN + one.getId());
                log.info("清除旧的管理员token - 用户: {}, 旧token: {}", account, oldToken);
                PoetryCache.remove(oldToken);
                PoetryCache.remove(CommonConst.ADMIN_TOKEN + one.getId());
                PoetryCache.remove(CommonConst.ADMIN_TOKEN_INTERVAL + one.getId());
            }
        } else {
            // 清除可能存在的旧token
            if (PoetryCache.get(CommonConst.USER_TOKEN + one.getId()) != null) {
                String oldToken = (String) PoetryCache.get(CommonConst.USER_TOKEN + one.getId());
                log.info("清除旧的用户token - 用户: {}, 旧token: {}", account, oldToken);
                PoetryCache.remove(oldToken);
                PoetryCache.remove(CommonConst.USER_TOKEN + one.getId());
                PoetryCache.remove(CommonConst.USER_TOKEN_INTERVAL + one.getId());
            }
        }

        // 生成新token
        if (isAdmin && !StringUtils.hasText(adminToken)) {
            String uuid = UUID.randomUUID().toString().replaceAll("-", "");
            adminToken = CommonConst.ADMIN_ACCESS_TOKEN + uuid;
            log.info("生成新的管理员token - 用户: {}, token: {}", account, adminToken);
            PoetryCache.put(adminToken, one, CommonConst.TOKEN_EXPIRE);
            PoetryCache.put(CommonConst.ADMIN_TOKEN + one.getId(), adminToken, CommonConst.TOKEN_EXPIRE);
        } else if (!isAdmin && !StringUtils.hasText(userToken)) {
            String uuid = UUID.randomUUID().toString().replaceAll("-", "");
            userToken = CommonConst.USER_ACCESS_TOKEN + uuid;
            log.info("生成新的用户token - 用户: {}, token: {}", account, userToken);
            PoetryCache.put(userToken, one, CommonConst.TOKEN_EXPIRE);
            PoetryCache.put(CommonConst.USER_TOKEN + one.getId(), userToken, CommonConst.TOKEN_EXPIRE);
        }

        // 构建返回数据
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(one, userVO);
        userVO.setPassword(null);
        userVO.setUserType(one.getUserType());
        if (isAdmin && one.getUserType() == PoetryEnum.USER_TYPE_ADMIN.getCode()) {
            userVO.setIsBoss(true);
        }

        if (isAdmin) {
            userVO.setAccessToken(adminToken);
        } else {
            userVO.setAccessToken(userToken);
        }
        return PoetryResult.success(userVO);
    }

    @Override
    public PoetryResult exit() {
        String token = PoetryUtil.getToken();
        Integer userId = PoetryUtil.getUserId();
        log.info("用户退出登录 - 用户ID: {}, token: {}", userId, token);
        
        if (token.contains(CommonConst.USER_ACCESS_TOKEN)) {
            PoetryCache.remove(CommonConst.USER_TOKEN + userId);
            TioWebsocketStarter tioWebsocketStarter = TioUtil.getTio();
            if (tioWebsocketStarter != null) {
                Tio.removeUser(tioWebsocketStarter.getServerTioConfig(), String.valueOf(userId), "用户退出登录");
            }
        } else if (token.contains(CommonConst.ADMIN_ACCESS_TOKEN)) {
            PoetryCache.remove(CommonConst.ADMIN_TOKEN + userId);
        }
        PoetryCache.remove(token);
        return PoetryResult.success();
    }

    @Override
    public PoetryResult<UserVO> regist(UserVO user) {
        String regex = "\\d{11}";
        if (user.getUsername().matches(regex)) {
            return PoetryResult.fail("用户名不能为11位数字！");
        }

        if (user.getUsername().contains("@")) {
            return PoetryResult.fail("用户名不能包含@！");
        }

        if (StringUtils.hasText(user.getPhoneNumber()) && StringUtils.hasText(user.getEmail())) {
            return PoetryResult.fail("手机号与邮箱只能选择其中一个！");
        }

        if (StringUtils.hasText(user.getPhoneNumber())) {
            Integer codeCache = (Integer) PoetryCache.get(CommonConst.FORGET_PASSWORD + user.getPhoneNumber() + "_1");
            if (codeCache == null || codeCache != Integer.parseInt(user.getCode())) {
                return PoetryResult.fail("验证码错误！");
            }
            PoetryCache.remove(CommonConst.FORGET_PASSWORD + user.getPhoneNumber() + "_1");
        } else if (StringUtils.hasText(user.getEmail())) {
            Integer codeCache = (Integer) PoetryCache.get(CommonConst.FORGET_PASSWORD + user.getEmail() + "_2");
            if (codeCache == null || codeCache != Integer.parseInt(user.getCode())) {
                return PoetryResult.fail("验证码错误！");
            }
            PoetryCache.remove(CommonConst.FORGET_PASSWORD + user.getEmail() + "_2");
        } else {
            return PoetryResult.fail("请输入邮箱或手机号！");
        }


        user.setPassword(new String(SecureUtil.aes(CommonConst.CRYPOTJS_KEY.getBytes(StandardCharsets.UTF_8)).decrypt(user.getPassword())));

        Long count = lambdaQuery().eq(User::getUsername, user.getUsername()).count();
        if (count != 0) {
            return PoetryResult.fail("用户名重复！");
        }
        if (StringUtils.hasText(user.getPhoneNumber())) {
            Long phoneNumberCount = lambdaQuery().eq(User::getPhoneNumber, user.getPhoneNumber()).count();
            if (phoneNumberCount != 0) {
                return PoetryResult.fail("手机号重复！");
            }
        } else if (StringUtils.hasText(user.getEmail())) {
            Long emailCount = lambdaQuery().eq(User::getEmail, user.getEmail()).count();
            if (emailCount != 0) {
                return PoetryResult.fail("邮箱重复！");
            }
        }

        User u = new User();
        u.setUsername(user.getUsername());
        u.setPhoneNumber(user.getPhoneNumber());
        u.setEmail(user.getEmail());
        u.setPassword(DigestUtils.md5DigestAsHex(user.getPassword().getBytes()));
        u.setAvatar(PoetryUtil.getRandomAvatar(null));
        save(u);

        User one = lambdaQuery().eq(User::getId, u.getId()).one();

        String userToken = CommonConst.USER_ACCESS_TOKEN + UUID.randomUUID().toString().replaceAll("-", "");
        PoetryCache.put(userToken, one, CommonConst.TOKEN_EXPIRE);
        PoetryCache.put(CommonConst.USER_TOKEN + one.getId(), userToken, CommonConst.TOKEN_EXPIRE);

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

        ImChatUserFriend imChatUser = new ImChatUserFriend();
        imChatUser.setUserId(one.getId());
        imChatUser.setFriendId(PoetryUtil.getAdminUser().getId());
        imChatUser.setRemark("站长");
        imChatUser.setFriendStatus(ImConfigConst.FRIEND_STATUS_PASS);
        imChatUserFriendMapper.insert(imChatUser);

        ImChatUserFriend imChatFriend = new ImChatUserFriend();
        imChatFriend.setUserId(PoetryUtil.getAdminUser().getId());
        imChatFriend.setFriendId(one.getId());
        imChatFriend.setFriendStatus(ImConfigConst.FRIEND_STATUS_PASS);
        imChatUserFriendMapper.insert(imChatFriend);

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

            Long count = lambdaQuery().eq(User::getUsername, user.getUsername()).ne(User::getId, PoetryUtil.getUserId()).count();
            if (count != 0) {
                return PoetryResult.fail("用户名重复！");
            }
        }
        User u = new User();
        u.setId(PoetryUtil.getUserId());
        u.setUsername(user.getUsername());
        u.setAvatar(user.getAvatar());
        u.setGender(user.getGender());
        u.setIntroduction(user.getIntroduction());
        updateById(u);
        User one = lambdaQuery().eq(User::getId, u.getId()).one();
        PoetryCache.put(PoetryUtil.getToken(), one, CommonConst.TOKEN_EXPIRE);
        PoetryCache.put(CommonConst.USER_TOKEN + one.getId(), PoetryUtil.getToken(), CommonConst.TOKEN_EXPIRE);

        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(one, userVO);
        userVO.setPassword(null);
        userVO.setUserType(one.getUserType());
        userVO.setAccessToken(PoetryUtil.getToken());
        return PoetryResult.success(userVO);
    }

    @Override
    public PoetryResult getCode(Integer flag) {
        User user = PoetryUtil.getCurrentUser();
        int i = new Random().nextInt(900000) + 100000;
        if (flag == 1) {
            if (!StringUtils.hasText(user.getPhoneNumber())) {
                return PoetryResult.fail("请先绑定手机号！");
            }

            log.info(user.getId() + "---" + user.getUsername() + "---" + "手机验证码---" + i);
        } else if (flag == 2) {
            if (!StringUtils.hasText(user.getEmail())) {
                return PoetryResult.fail("请先绑定邮箱！");
            }

            log.info(user.getId() + "---" + user.getUsername() + "---" + "邮箱验证码---" + i);

            List<String> mail = new ArrayList<>();
            mail.add(user.getEmail());
            String text = getCodeMail(i);
            WebInfo webInfo = (WebInfo) PoetryCache.get(CommonConst.WEB_INFO);

            // 检查邮箱配置是否存在
            if (mailUtil == null || !mailUtil.isEmailConfigured()) {
                return PoetryResult.fail("邮箱服务未配置，请联系管理员在后台设置邮箱配置");
            }

            AtomicInteger count = (AtomicInteger) PoetryCache.get(CommonConst.CODE_MAIL + mail.get(0));
            if (count == null || count.get() < CommonConst.CODE_MAIL_COUNT) {
                mailUtil.sendMailMessage(mail, "您有一封来自" + (webInfo == null ? "POETIZE" : webInfo.getWebName()) + "的回执！", text);
                if (count == null) {
                    PoetryCache.put(CommonConst.CODE_MAIL + mail.get(0), new AtomicInteger(1), CommonConst.CODE_EXPIRE);
                } else {
                    count.incrementAndGet();
                }
            } else {
                return PoetryResult.fail("验证码发送次数过多，请明天再试！");
            }
        }
        PoetryCache.put(CommonConst.USER_CODE + PoetryUtil.getUserId() + "_" + flag, Integer.valueOf(i), 300);
        return PoetryResult.success();
    }

    @Override
    public PoetryResult getCodeForBind(String place, Integer flag) {
        int i = new Random().nextInt(900000) + 100000;
        if (flag == 1) {
            log.info(place + "---" + "手机验证码---" + i);
        } else if (flag == 2) {
            log.info(place + "---" + "邮箱验证码---" + i);
            List<String> mail = new ArrayList<>();
            mail.add(place);
            String text = getCodeMail(i);
            WebInfo webInfo = (WebInfo) PoetryCache.get(CommonConst.WEB_INFO);

            // 检查邮箱配置是否存在
            if (mailUtil == null || !mailUtil.isEmailConfigured()) {
                return PoetryResult.fail("邮箱服务未配置，请联系管理员在后台设置邮箱配置");
            }

            AtomicInteger count = (AtomicInteger) PoetryCache.get(CommonConst.CODE_MAIL + mail.get(0));
            if (count == null || count.get() < CommonConst.CODE_MAIL_COUNT) {
                mailUtil.sendMailMessage(mail, "您有一封来自" + (webInfo == null ? "POETIZE" : webInfo.getWebName()) + "的回执！", text);
                if (count == null) {
                    PoetryCache.put(CommonConst.CODE_MAIL + mail.get(0), new AtomicInteger(1), CommonConst.CODE_EXPIRE);
                } else {
                    count.incrementAndGet();
                }
            } else {
                return PoetryResult.fail("验证码发送次数过多，请明天再试！");
            }
        }
        PoetryCache.put(CommonConst.USER_CODE + PoetryUtil.getUserId() + "_" + place + "_" + flag, Integer.valueOf(i), 300);
        return PoetryResult.success();
    }

    @Override
    public PoetryResult<UserVO> updateSecretInfo(String place, Integer flag, String code, String password) {
        password = new String(SecureUtil.aes(CommonConst.CRYPOTJS_KEY.getBytes(StandardCharsets.UTF_8)).decrypt(password));

        User user = PoetryUtil.getCurrentUser();
        if ((flag == 1 || flag == 2) && !DigestUtils.md5DigestAsHex(password.getBytes()).equals(user.getPassword())) {
            return PoetryResult.fail("密码错误！");
        }
        if ((flag == 1 || flag == 2) && !StringUtils.hasText(code)) {
            return PoetryResult.fail("请输入验证码！");
        }
        User updateUser = new User();
        updateUser.setId(user.getId());
        if (flag == 1) {
            Long count = lambdaQuery().eq(User::getPhoneNumber, place).count();
            if (count != 0) {
                return PoetryResult.fail("手机号重复！");
            }
            Integer codeCache = (Integer) PoetryCache.get(CommonConst.USER_CODE + PoetryUtil.getUserId() + "_" + place + "_" + flag);
            if (codeCache != null && codeCache.intValue() == Integer.parseInt(code)) {

                PoetryCache.remove(CommonConst.USER_CODE + PoetryUtil.getUserId() + "_" + place + "_" + flag);

                updateUser.setPhoneNumber(place);
            } else {
                return PoetryResult.fail("验证码错误！");
            }

        } else if (flag == 2) {
            Long count = lambdaQuery().eq(User::getEmail, place).count();
            if (count != 0) {
                return PoetryResult.fail("邮箱重复！");
            }
            Integer codeCache = (Integer) PoetryCache.get(CommonConst.USER_CODE + PoetryUtil.getUserId() + "_" + place + "_" + flag);
            if (codeCache != null && codeCache.intValue() == Integer.parseInt(code)) {

                PoetryCache.remove(CommonConst.USER_CODE + PoetryUtil.getUserId() + "_" + place + "_" + flag);

                updateUser.setEmail(place);
            } else {
                return PoetryResult.fail("验证码错误！");
            }
        } else if (flag == 3) {
            if (DigestUtils.md5DigestAsHex(place.getBytes()).equals(user.getPassword())) {
                updateUser.setPassword(DigestUtils.md5DigestAsHex(password.getBytes()));
            } else {
                return PoetryResult.fail("密码错误！");
            }
        }
        updateById(updateUser);

        User one = lambdaQuery().eq(User::getId, user.getId()).one();
        PoetryCache.put(PoetryUtil.getToken(), one, CommonConst.TOKEN_EXPIRE);
        PoetryCache.put(CommonConst.USER_TOKEN + one.getId(), PoetryUtil.getToken(), CommonConst.TOKEN_EXPIRE);

        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(one, userVO);
        userVO.setPassword(null);
        return PoetryResult.success(userVO);
    }

    @Override
    public PoetryResult getCodeForForgetPassword(String place, Integer flag) {
        int i = new Random().nextInt(900000) + 100000;
        if (flag == 1) {
            log.info(place + "---" + "手机验证码---" + i);
        } else if (flag == 2) {
            log.info(place + "---" + "邮箱验证码---" + i);

            List<String> mail = new ArrayList<>();
            mail.add(place);
            String text = getCodeMail(i);
            WebInfo webInfo = (WebInfo) PoetryCache.get(CommonConst.WEB_INFO);

            // 检查邮箱配置是否存在
            if (mailUtil == null || !mailUtil.isEmailConfigured()) {
                return PoetryResult.fail("邮箱服务未配置，请联系管理员在后台设置邮箱配置");
            }

            AtomicInteger count = (AtomicInteger) PoetryCache.get(CommonConst.CODE_MAIL + mail.get(0));
            if (count == null || count.get() < CommonConst.CODE_MAIL_COUNT) {
                mailUtil.sendMailMessage(mail, "您有一封来自" + (webInfo == null ? "POETIZE" : webInfo.getWebName()) + "的回执！", text);
                if (count == null) {
                    PoetryCache.put(CommonConst.CODE_MAIL + mail.get(0), new AtomicInteger(1), CommonConst.CODE_EXPIRE);
                } else {
                    count.incrementAndGet();
                }
            } else {
                return PoetryResult.fail("验证码发送次数过多，请明天再试！");
            }
        }
        PoetryCache.put(CommonConst.FORGET_PASSWORD + place + "_" + flag, Integer.valueOf(i), 300);
        return PoetryResult.success();
    }

    @Override
    public PoetryResult updateForForgetPassword(String place, Integer flag, String code, String password) {
        password = new String(SecureUtil.aes(CommonConst.CRYPOTJS_KEY.getBytes(StandardCharsets.UTF_8)).decrypt(password));

        Integer codeCache = (Integer) PoetryCache.get(CommonConst.FORGET_PASSWORD + place + "_" + flag);
        if (codeCache == null || codeCache != Integer.parseInt(code)) {
            return PoetryResult.fail("验证码错误！");
        }

        PoetryCache.remove(CommonConst.FORGET_PASSWORD + place + "_" + flag);

        if (flag == 1) {
            User user = lambdaQuery().eq(User::getPhoneNumber, place).one();
            if (user == null) {
                return PoetryResult.fail("该手机号未绑定账号！");
            }

            if (!user.getUserStatus()) {
                return PoetryResult.fail("账号被冻结！");
            }

            lambdaUpdate().eq(User::getPhoneNumber, place).set(User::getPassword, DigestUtils.md5DigestAsHex(password.getBytes())).update();
            PoetryCache.remove(CommonConst.USER_CACHE + user.getId().toString());
        } else if (flag == 2) {
            User user = lambdaQuery().eq(User::getEmail, place).one();
            if (user == null) {
                return PoetryResult.fail("该邮箱未绑定账号！");
            }

            if (!user.getUserStatus()) {
                return PoetryResult.fail("账号被冻结！");
            }

            lambdaUpdate().eq(User::getEmail, place).set(User::getPassword, DigestUtils.md5DigestAsHex(password.getBytes())).update();
            PoetryCache.remove(CommonConst.USER_CACHE + user.getId().toString());
        }

        return PoetryResult.success();
    }

    @Override
    public PoetryResult<Page> listUser(BaseRequestVO baseRequestVO) {
        LambdaQueryChainWrapper<User> lambdaQuery = lambdaQuery();

        if (baseRequestVO.getUserStatus() != null) {
            lambdaQuery.eq(User::getUserStatus, baseRequestVO.getUserStatus());
        }

        if (baseRequestVO.getUserType() != null) {
            lambdaQuery.eq(User::getUserType, baseRequestVO.getUserType());
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

        List<User> records = resultPage.getRecords();
        if (!CollectionUtils.isEmpty(records)) {
            records.forEach(u -> {
                u.setPassword(null);
                u.setOpenId(null);
            });
        }
        
        return PoetryResult.success(resultPage);
    }

    @Override
    public PoetryResult<List<UserVO>> getUserByUsername(String username) {
        List<User> users = lambdaQuery().select(User::getId, User::getUsername, User::getAvatar, User::getGender, User::getIntroduction).like(User::getUsername, username).last("limit 5").list();
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
        userToken = new String(SecureUtil.aes(CommonConst.CRYPOTJS_KEY.getBytes(StandardCharsets.UTF_8)).decrypt(userToken));

        if (!StringUtils.hasText(userToken)) {
            throw new PoetryRuntimeException("未登陆，请登陆后再进行操作！");
        }

        User user = (User) PoetryCache.get(userToken);

        if (user == null) {
            throw new PoetryRuntimeException("登录已过期，请重新登陆！");
        }

        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        userVO.setPassword(null);
        userVO.setUserType(user.getUserType());
        userVO.setAccessToken(userToken);

        return PoetryResult.success(userVO);
    }

    @Override
    public PoetryResult<UserVO> subscribe(Integer labelId, Boolean flag) {
        UserVO userVO = null;
        User one = lambdaQuery().eq(User::getId, PoetryUtil.getUserId()).one();
        List<Integer> sub = JSON.parseArray(one.getSubscribe(), Integer.class);
        if (sub == null) sub = new ArrayList<>();
        if (flag) {
            if (!sub.contains(labelId)) {
                sub.add(labelId);
                User user = new User();
                user.setId(one.getId());
                user.setSubscribe(JSON.toJSONString(sub));
                updateById(user);

                userVO = new UserVO();
                BeanUtils.copyProperties(one, userVO);
                userVO.setPassword(null);
                userVO.setSubscribe(user.getSubscribe());
                userVO.setAccessToken(PoetryUtil.getToken());
            }
        } else {
            if (sub.contains(labelId)) {
                sub.remove(labelId);
                User user = new User();
                user.setId(one.getId());
                user.setSubscribe(JSON.toJSONString(sub));
                updateById(user);

                userVO = new UserVO();
                BeanUtils.copyProperties(one, userVO);
                userVO.setPassword(null);
                userVO.setSubscribe(user.getSubscribe());
                userVO.setAccessToken(PoetryUtil.getToken());
            }
        }
        return PoetryResult.success(userVO);
    }

    @Override
    public PoetryResult<UserVO> thirdLogin(String provider, String uid, String username, String email, String avatar) {
        if (!StringUtils.hasText(provider) || !StringUtils.hasText(uid)) {
            return PoetryResult.fail("第三方登录信息不完整");
        }

        User existUser = lambdaQuery()
                .eq(User::getPlatformType, provider)
                .eq(User::getUid, uid)
                .one();

        if (existUser == null) {
            String finalUsername = username;
            if (!StringUtils.hasText(finalUsername)) {
                finalUsername = provider + "_user_" + System.currentTimeMillis();
            }
            
            int count = 0;
            String uniqueUsername = finalUsername;
            while (lambdaQuery().eq(User::getUsername, uniqueUsername).count() > 0) {
                uniqueUsername = finalUsername + "_" + (++count);
            }

            User newUser = new User();
            newUser.setUsername(uniqueUsername);
            newUser.setPlatformType(provider);
            newUser.setUid(uid);
            newUser.setEmail(email);
            newUser.setAvatar(avatar);
            newUser.setUserStatus(true);
            newUser.setUserType(PoetryEnum.USER_TYPE_USER.getCode());
            newUser.setGender(PoetryEnum.USER_GENDER_NONE.getCode());
            
            save(newUser);
            
            ImChatGroupUser imChatGroupUser = new ImChatGroupUser();
            imChatGroupUser.setGroupId(ImConfigConst.DEFAULT_GROUP_ID);
            imChatGroupUser.setUserId(newUser.getId());
            imChatGroupUser.setAdminFlag(false);
            imChatGroupUser.setUserStatus(ImConfigConst.GROUP_USER_STATUS_PASS);
            imChatGroupUserMapper.insert(imChatGroupUser);
            
            existUser = newUser;
        }

        String userToken = "";
        if (PoetryCache.get(CommonConst.USER_TOKEN + existUser.getId()) != null) {
            userToken = (String) PoetryCache.get(CommonConst.USER_TOKEN + existUser.getId());
        }
        
        if (!StringUtils.hasText(userToken)) {
            String uuid = UUID.randomUUID().toString().replaceAll("-", "");
            userToken = CommonConst.USER_ACCESS_TOKEN + uuid;
            PoetryCache.put(userToken, existUser, CommonConst.TOKEN_EXPIRE);
            PoetryCache.put(CommonConst.USER_TOKEN + existUser.getId(), userToken, CommonConst.TOKEN_EXPIRE);
        }

        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(existUser, userVO);
        userVO.setPassword(null);
        userVO.setAccessToken(userToken);
        
        return PoetryResult.success(userVO);
    }

    private String getCodeMail(int i) {
        WebInfo webInfo = (WebInfo) PoetryCache.get(CommonConst.WEB_INFO);
        String webName = (webInfo == null ? "POETIZE" : webInfo.getWebName());
        return String.format(mailUtil.getMailText(),
                webName,
                String.format(MailUtil.imMail, PoetryUtil.getAdminUser().getUsername()),
                PoetryUtil.getAdminUser().getUsername(),
                String.format(codeFormat, i),
                "",
                webName);
    }
}
