package com.ld.poetry.config;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.ld.poetry.controller.FriendController;
import com.ld.poetry.controller.ResourceAggregationController;
import com.ld.poetry.dao.HistoryInfoMapper;
import com.ld.poetry.dao.WebInfoMapper;
import com.ld.poetry.entity.*;
import com.ld.poetry.service.CacheService;
import com.ld.poetry.service.FamilyService;
import com.ld.poetry.service.SysConfigService;
import com.ld.poetry.service.UserService;
import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.enums.PoetryEnum;
import com.ld.poetry.utils.CommonQuery;
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

    @Autowired
    private SysConfigService sysConfigService;

    @Autowired
    private CommonQuery commonQuery;

    @Autowired
    private ResourceAggregationController resourceAggregationController;

    @Autowired
    private FriendController friendController;

    @Override
    public void run(ApplicationArguments args) {
        initWebInfoCache();
        initAdminUserCache();
        initHistoryCache();
        initPublicSysConfigCache();
        initSortInfoCache();
        initAsideBootstrapCache();
        initFriendListCache();

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

    /**
     * 初始化全量公开系统配置 Map 缓存
     * <p>预热 bootstrap 聚合接口中的 sysConfig 数据，避免首个用户承担 DB 查询延迟。
     */
    private void initPublicSysConfigCache() {
        try {
            List<SysConfig> sysConfigs = new LambdaQueryChainWrapper<>(sysConfigService.getBaseMapper())
                    .eq(SysConfig::getConfigType, Integer.toString(PoetryEnum.SYS_CONFIG_PUBLIC.getCode()))
                    .list();
            Map<String, String> configMap = sysConfigs.stream().collect(Collectors.toMap(
                    SysConfig::getConfigKey,
                    SysConfig::getConfigValue,
                    (oldValue, newValue) -> newValue
            ));
            cacheService.cachePublicSysConfigMap(configMap);
        } catch (Exception e) {
            log.error("预热公开系统配置 Map 缓存失败", e);
        }
    }

    /**
     * 预热分类标签树缓存
     * <p>getSortInfo 内部已实现「读缓存未命中则查 DB 并回填」逻辑，预热只需调用一次触发回填。
     */
    private void initSortInfoCache() {
        try {
            commonQuery.getSortInfo();
        } catch (Exception e) {
            log.error("预热分类标签树缓存失败", e);
        }
    }

    /**
     * 预热侧边栏首屏聚合缓存
     * <p>loadAsideBootstrapData 内部已实现「读缓存未命中则查 DB 并回填」逻辑，预热只需调用一次触发回填，
     * 避免首个用户请求承担 3 次 DB 查询延迟。
     */
    private void initAsideBootstrapCache() {
        try {
            resourceAggregationController.loadAsideBootstrapData();
        } catch (Exception e) {
            log.error("预热侧边栏首屏聚合缓存失败", e);
        }
    }

    /**
     * 预热友人帐友链列表缓存
     * <p>loadFriendListData 内部已实现「读缓存未命中则查 DB 并回填」逻辑，预热只需调用一次触发回填，
     * 避免首个用户访问友人帐页面时承担 DB 查询延迟。
     */
    private void initFriendListCache() {
        try {
            friendController.loadFriendListData();
        } catch (Exception e) {
            log.error("预热友人帐友链列表缓存失败", e);
        }
    }
}
