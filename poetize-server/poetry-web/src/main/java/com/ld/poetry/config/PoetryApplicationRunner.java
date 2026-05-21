package com.ld.poetry.config;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.ld.poetry.dao.HistoryInfoMapper;
import com.ld.poetry.dao.WebInfoMapper;
import com.ld.poetry.entity.*;
import com.ld.poetry.service.CacheService;
import com.ld.poetry.service.FamilyService;
import com.ld.poetry.service.UserService;
import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.enums.PoetryEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

/**
 * 核心缓存预热 Runner
 *
 * <p>
 * 应用启动时加载基础缓存数据（网站信息、管理员信息、访问历史等），
 * 是系统正常运行的必要前置步骤。
 *
 * @author LeapYa
 * @since 2025-06-21
 * @see SitemapWarmupRunner
 * @see PrerenderStartupRunner
 */
@Component
@Order(10)
@Slf4j
public class PoetryApplicationRunner implements ApplicationRunner {

    @Value("${store.type}")
    private String defaultType;

    @Autowired
    private WebInfoMapper webInfoMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private FamilyService familyService;

    @Autowired
    private HistoryInfoMapper historyInfoMapper;

    @Autowired
    private CacheService cacheService;

    @Override
    public void run(ApplicationArguments args) {
        initWebInfoCache();
        initAdminUserCache();
        initHistoryCache();

        // WebSocket 由 Spring WebSocket 自动管理
        log.info("Spring WebSocket 服务已自动配置，端点: /ws/im");
    }

    /**
     * 初始化网站信息缓存
     */
    private void initWebInfoCache() {
        LambdaQueryChainWrapper<WebInfo> wrapper = new LambdaQueryChainWrapper<>(webInfoMapper);
        List<WebInfo> list = wrapper.list();
        if (CollectionUtils.isEmpty(list)) {
            log.warn("未找到网站基本信息，请检查数据库");
            return;
        }

        WebInfo webInfo = list.get(0);
        webInfo.setDefaultStoreType(defaultType);

        if (webInfo.getEnableWaifu() == null) {
            webInfo.setEnableWaifu(false);
        }
        if (webInfo.getStatus() == null) {
            webInfo.setStatus(true);
            log.info("WebInfo status 字段为 null，设置为默认值 true");
        }

        cacheService.cacheWebInfo(webInfo);
        log.info("网站基本信息已加载到 Redis 缓存（永久） - WebName: {}, EnableWaifu: {}, Status: {}",
                webInfo.getWebName(), webInfo.getEnableWaifu(), webInfo.getStatus());
    }

    /**
     * 初始化管理员用户和家庭信息缓存
     */
    private void initAdminUserCache() {
        User admin = userService.lambdaQuery()
                .eq(User::getUserType, PoetryEnum.USER_TYPE_ADMIN.getCode())
                .one();

        if (admin == null) {
            log.error("未找到管理员用户，请检查数据库！应用可能无法正常工作");
            return;
        }

        cacheService.cacheAdminUser(admin);
        log.info("管理员用户信息已加载到 Redis 缓存（永久） - Username: {}, ID: {}, Email: {}",
                admin.getUsername(), admin.getId(), admin.getEmail());

        // 管理员家庭信息
        Family family = familyService.lambdaQuery()
                .eq(Family::getUserId, admin.getId())
                .one();
        if (family != null) {
            cacheService.cacheAdminFamily(family);
            log.info("管理员家庭信息已加载到缓存");
        }
    }

    /**
     * 初始化历史访问和 IP 统计缓存
     */
    private void initHistoryCache() {
        // 当日访问记录
        List<HistoryInfo> infoList = new LambdaQueryChainWrapper<>(historyInfoMapper)
                .select(HistoryInfo::getIp, HistoryInfo::getUserId)
                .ge(HistoryInfo::getCreateTime, LocalDateTime.now().with(LocalTime.MIN))
                .list();

        cacheService.cacheIpHistory(new CopyOnWriteArraySet<>(infoList.stream()
                .map(info -> info.getIp() + (info.getUserId() != null ? "_" + info.getUserId().toString() : ""))
                .collect(Collectors.toList())));

        // IP 统计汇总
        List<String> ignoredIps = cacheService.getVisitIgnoreIpList();
        Map<String, Object> history = new HashMap<>();
        history.put(CommonConst.IP_HISTORY_PROVINCE, historyInfoMapper.getHistoryByProvince(ignoredIps));
        history.put(CommonConst.IP_HISTORY_IP, historyInfoMapper.getHistoryByIp(ignoredIps));
        history.put(CommonConst.IP_HISTORY_HOUR, historyInfoMapper.getHistoryBy24Hour(ignoredIps));
        history.put(CommonConst.IP_HISTORY_COUNT, historyInfoMapper.getHistoryCount(ignoredIps));
        cacheService.cacheIpHistoryStatistics(history);
    }
}
