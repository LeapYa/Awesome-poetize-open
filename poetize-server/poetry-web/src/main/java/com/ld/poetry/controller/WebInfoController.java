package com.ld.poetry.controller;


import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.dao.*;
import com.ld.poetry.entity.*;
import com.ld.poetry.service.WebInfoService;
import com.ld.poetry.utils.*;
import com.ld.poetry.utils.cache.PoetryCache;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 网站信息表 前端控制器
 * </p>
 *
 * @author sara
 * @since 2021-09-14
 */
@SuppressWarnings("unchecked")
@RestController
@RequestMapping("/webInfo")
public class WebInfoController {

    @Value("${store.type}")
    private String defaultType;

    @Autowired
    private WebInfoService webInfoService;

    @Autowired
    private HistoryInfoMapper historyInfoMapper;

    @Autowired
    private CommonQuery commonQuery;

    // API配置的缓存键
    private static final String API_CONFIG_CACHE_KEY = "API_CONFIG";


    /**
     * 更新网站信息
     */
    @LoginCheck(0)
    @PostMapping("/updateWebInfo")
    public PoetryResult<WebInfo> updateWebInfo(@RequestBody WebInfo webInfo) {
        webInfoService.updateById(webInfo);

        LambdaQueryChainWrapper<WebInfo> wrapper = new LambdaQueryChainWrapper<>(webInfoService.getBaseMapper());
        List<WebInfo> list = wrapper.list();
        if (!CollectionUtils.isEmpty(list)) {
            list.get(0).setDefaultStoreType(defaultType);
            PoetryCache.put(CommonConst.WEB_INFO, list.get(0));
        }
        return PoetryResult.success();
    }


    /**
     * 获取网站信息
     */
    @GetMapping("/getWebInfo")
    public PoetryResult<WebInfo> getWebInfo() {
        WebInfo webInfo = (WebInfo) PoetryCache.get(CommonConst.WEB_INFO);
        if (webInfo != null) {
            WebInfo result = new WebInfo();
            BeanUtils.copyProperties(webInfo, result);
            result.setRandomAvatar(null);
            result.setRandomName(null);
            result.setWaifuJson(null);

            webInfo.setHistoryAllCount(((Long) ((Map<String, Object>) PoetryCache.get(CommonConst.IP_HISTORY_STATISTICS)).get(CommonConst.IP_HISTORY_COUNT)).toString());
            webInfo.setHistoryDayCount(Integer.toString(((List<Map<String, Object>>) ((Map<String, Object>) PoetryCache.get(CommonConst.IP_HISTORY_STATISTICS)).get(CommonConst.IP_HISTORY_HOUR)).size()));
            return PoetryResult.success(result);
        }
        return PoetryResult.success();
    }

    /**
     * 获取网站统计信息
     */
    @LoginCheck(0)
    @GetMapping("/getHistoryInfo")
    public PoetryResult<Map<String, Object>> getHistoryInfo() {
        Map<String, Object> result = new HashMap<>();

        Map<String, Object> history = (Map<String, Object>) PoetryCache.get(CommonConst.IP_HISTORY_STATISTICS);
        List<HistoryInfo> infoList = new LambdaQueryChainWrapper<>(historyInfoMapper)
                .select(HistoryInfo::getIp, HistoryInfo::getUserId, HistoryInfo::getNation, HistoryInfo::getProvince, HistoryInfo::getCity)
                .ge(HistoryInfo::getCreateTime, LocalDateTime.now().with(LocalTime.MIN))
                .list();

        result.put(CommonConst.IP_HISTORY_PROVINCE, history.get(CommonConst.IP_HISTORY_PROVINCE));
        result.put(CommonConst.IP_HISTORY_IP, history.get(CommonConst.IP_HISTORY_IP));
        result.put(CommonConst.IP_HISTORY_COUNT, history.get(CommonConst.IP_HISTORY_COUNT));
        List<Map<String, Object>> ipHistoryCount = (List<Map<String, Object>>) history.get(CommonConst.IP_HISTORY_HOUR);
        result.put("ip_count_yest", ipHistoryCount.stream().map(m -> m.get("ip")).distinct().count());
        result.put("username_yest", ipHistoryCount.stream().map(m -> {
            Object userId = m.get("user_id");
            if (userId != null) {
                User user = commonQuery.getUser(Integer.valueOf(userId.toString()));
                if (user != null) {
                    Map<String, String> userInfo = new HashMap<>();
                    userInfo.put("avatar", user.getAvatar());
                    userInfo.put("username", user.getUsername());
                    return userInfo;
                }
            }
            return null;
        }).filter(Objects::nonNull).collect(Collectors.toList()));
        result.put("ip_count_today", infoList.stream().map(HistoryInfo::getIp).distinct().count());
        result.put("username_today", infoList.stream().map(m -> {
            Integer userId = m.getUserId();
            if (userId != null) {
                User user = commonQuery.getUser(userId);
                if (user != null) {
                    Map<String, String> userInfo = new HashMap<>();
                    userInfo.put("avatar", user.getAvatar());
                    userInfo.put("username", user.getUsername());
                    return userInfo;
                }
            }
            return null;
        }).filter(Objects::nonNull).collect(Collectors.toList()));

        List<Map<String, Object>> list = infoList.stream()
                .map(HistoryInfo::getProvince).filter(Objects::nonNull)
                .collect(Collectors.groupingBy(m -> m, Collectors.counting()))
                .entrySet().stream()
                .map(entry -> {
                    HashMap<String, Object> map = new HashMap<>();
                    map.put("province", entry.getKey());
                    map.put("num", entry.getValue());
                    return map;
                })
                .sorted((o1, o2) -> Long.valueOf(o2.get("num").toString()).compareTo(Long.valueOf(o1.get("num").toString())))
                .collect(Collectors.toList());

        result.put("province_today", list);

        return PoetryResult.success(result);
    }

    /**
     * 获取赞赏
     */
    @GetMapping("/getAdmire")
    public PoetryResult<List<User>> getAdmire() {
        return PoetryResult.success(commonQuery.getAdmire());
    }

    /**
     * 获取看板娘消息
     */
    @GetMapping("/getWaifuJson")
    public String getWaifuJson() {
        WebInfo webInfo = (WebInfo) PoetryCache.get(CommonConst.WEB_INFO);
        if (webInfo != null && StringUtils.hasText(webInfo.getWaifuJson())) {
            return webInfo.getWaifuJson();
        }
        return "{}";
    }

    /**
     * 清除分类信息缓存
     */
    @GetMapping("/clearSortCache")
    public PoetryResult clearSortCache() {
        PoetryCache.remove(CommonConst.SORT_INFO);
        return PoetryResult.success();
    }

    /**
     * 获取API配置
     */
    @LoginCheck(0)
    @GetMapping("/getApiConfig")
    public PoetryResult<Map<String, Object>> getApiConfig() {
        WebInfo webInfo = (WebInfo) PoetryCache.get(CommonConst.WEB_INFO);
        if (webInfo == null) {
            LambdaQueryChainWrapper<WebInfo> wrapper = new LambdaQueryChainWrapper<>(webInfoService.getBaseMapper());
            List<WebInfo> list = wrapper.list();
            if (!CollectionUtils.isEmpty(list)) {
                webInfo = list.get(0);
                webInfo.setDefaultStoreType(defaultType);
                PoetryCache.put(CommonConst.WEB_INFO, webInfo);
            } else {
                webInfo = new WebInfo();
            }
        }
        
        Map<String, Object> apiConfig = new HashMap<>();
        apiConfig.put("enabled", webInfo.getApiEnabled() != null ? webInfo.getApiEnabled() : false);
        apiConfig.put("apiKey", webInfo.getApiKey() != null ? webInfo.getApiKey() : generateApiKey());
        
        return PoetryResult.success(apiConfig);
    }

    /**
     * 保存API配置
     */
    @LoginCheck(0)
    @PostMapping("/saveApiConfig")
    public PoetryResult saveApiConfig(@RequestBody Map<String, Object> apiConfig) {
        WebInfo webInfo = (WebInfo) PoetryCache.get(CommonConst.WEB_INFO);
        if (webInfo == null) {
            LambdaQueryChainWrapper<WebInfo> wrapper = new LambdaQueryChainWrapper<>(webInfoService.getBaseMapper());
            List<WebInfo> list = wrapper.list();
            if (!CollectionUtils.isEmpty(list)) {
                webInfo = list.get(0);
            } else {
                return PoetryResult.fail("网站信息不存在");
            }
        }
        
        Boolean enabled = (Boolean) apiConfig.get("enabled");
        String apiKey = (String) apiConfig.get("apiKey");
        
        // 如果提交的配置不包含apiKey，生成一个新的
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = generateApiKey();
        }
        
        // 更新数据库
        WebInfo updateInfo = new WebInfo();
        updateInfo.setId(webInfo.getId());
        updateInfo.setApiEnabled(enabled);
        updateInfo.setApiKey(apiKey);
        webInfoService.updateById(updateInfo);
        
        // 更新缓存
        webInfo.setApiEnabled(enabled);
        webInfo.setApiKey(apiKey);
        PoetryCache.put(CommonConst.WEB_INFO, webInfo);
        
        return PoetryResult.success();
    }

    /**
     * 重新生成API密钥
     */
    @LoginCheck(0)
    @PostMapping("/regenerateApiKey")
    public PoetryResult<String> regenerateApiKey() {
        WebInfo webInfo = (WebInfo) PoetryCache.get(CommonConst.WEB_INFO);
        if (webInfo == null) {
            LambdaQueryChainWrapper<WebInfo> wrapper = new LambdaQueryChainWrapper<>(webInfoService.getBaseMapper());
            List<WebInfo> list = wrapper.list();
            if (!CollectionUtils.isEmpty(list)) {
                webInfo = list.get(0);
            } else {
                return PoetryResult.fail("网站信息不存在");
            }
        }
        
        String newApiKey = generateApiKey();
        
        // 更新数据库
        WebInfo updateInfo = new WebInfo();
        updateInfo.setId(webInfo.getId());
        updateInfo.setApiKey(newApiKey);
        webInfoService.updateById(updateInfo);
        
        // 更新缓存
        webInfo.setApiKey(newApiKey);
        PoetryCache.put(CommonConst.WEB_INFO, webInfo);
        
        return PoetryResult.success(newApiKey);
    }
    
    /**
     * 生成API密钥
     */
    private String generateApiKey() {
        return UUID.randomUUID().toString().replaceAll("-", "") + 
               UUID.randomUUID().toString().replaceAll("-", "").substring(0, 8);
    }
}

