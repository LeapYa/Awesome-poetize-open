package com.ld.poetry.controller;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.dao.TreeHoleMapper;
import com.ld.poetry.dao.WebInfoMapper;
import com.ld.poetry.entity.*;
import com.ld.poetry.vo.BaseRequestVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;
import com.ld.poetry.enums.PoetryEnum;
import com.ld.poetry.utils.IpUtil;
import org.springframework.core.env.Environment;
import lombok.extern.slf4j.Slf4j;
import com.ld.poetry.service.CacheService;
import com.ld.poetry.service.WebInfoService;
import com.ld.poetry.config.PoetryApplicationRunner;
import java.util.HashMap;
import java.util.Locale;

/**
 * <p>
 * 后台 前端控制器
 * </p>
 *
 * @author sara
 * @since 2021-08-13
 */
@RestController
@RequestMapping("/admin")
@Slf4j
public class AdminController {

    @Autowired
    private WebInfoMapper webInfoMapper;

    @Autowired
    private TreeHoleMapper treeHoleMapper;

    @Autowired
    private Environment env;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private com.ld.poetry.service.PasswordUpgradeService passwordUpgradeService;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private WebInfoService webInfoService;

    @Autowired
    private PoetryApplicationRunner poetryApplicationRunner;

    @Autowired
    private com.ld.poetry.service.SitemapService sitemapService;

    @Autowired
    private com.ld.poetry.service.SearchEnginePushService searchEnginePushService;

    @Autowired
    private com.ld.poetry.service.RobotsService robotsService;

    @Autowired
    private com.ld.poetry.utils.BanRegionNormalizer banRegionNormalizer;

    /**
     * 获取网站信息
     */
    @GetMapping("/webInfo/getAdminWebInfo")
    @LoginCheck(0)
    public PoetryResult<WebInfo> getWebInfo() {
        LambdaQueryChainWrapper<WebInfo> wrapper = new LambdaQueryChainWrapper<>(webInfoMapper);
        List<WebInfo> list = wrapper.list();
        if (!CollectionUtils.isEmpty(list)) {
            return PoetryResult.success(list.get(0));
        } else {
            return PoetryResult.success();
        }
    }

    /**
     * Boss查询树洞
     */
    @PostMapping("/treeHole/boss/list")
    @LoginCheck(1)
    public PoetryResult<Page> listBossTreeHole(@RequestBody BaseRequestVO baseRequestVO) {
        LambdaQueryChainWrapper<TreeHole> wrapper = new LambdaQueryChainWrapper<>(treeHoleMapper);
        Page<TreeHole> page = new Page<>(baseRequestVO.getCurrent(), baseRequestVO.getSize());
        Page<TreeHole> resultPage = wrapper.orderByDesc(TreeHole::getCreateTime).page(page);
        return PoetryResult.success(resultPage);
    }

    /**
     * 获取密码升级统计信息
     */
    @GetMapping("/password/upgrade/statistics")
    @LoginCheck(0)
    public PoetryResult<Map<String, Object>> getPasswordUpgradeStatistics() {
        try {
            Map<String, Object> statistics = passwordUpgradeService.getUpgradeStatistics();
            return PoetryResult.success(statistics);
        } catch (Exception e) {
            log.error("获取密码升级统计失败", e);
            return PoetryResult.fail("获取密码升级统计失败，请稍后重试");
        }
    }

    /**
     * 获取密码安全报告
     */
    @GetMapping("/password/security/report")
    @LoginCheck(0)
    public PoetryResult<String> getPasswordSecurityReport() {
        try {
            String report = passwordUpgradeService.generateSecurityReport();
            return PoetryResult.success(report);
        } catch (Exception e) {
            log.error("生成密码安全报告失败", e);
            return PoetryResult.fail("生成密码安全报告失败，请稍后重试");
        }
    }

    /**
     * 获取管理员网站详细信息（包含敏感配置）
     */
    @GetMapping("/webInfo/getAdminWebInfoDetails")
    @LoginCheck(0)
    public PoetryResult<WebInfo> getAdminWebInfoDetails(
            @RequestParam(value = "refresh", defaultValue = "false") boolean forceRefresh) {
        try {
            if (forceRefresh) {
                // 强制刷新缓存
                cacheService.evictWebInfo();
                log.info("强制刷新网站信息缓存");
            }

            // 从缓存获取网站信息
            WebInfo webInfo = cacheService.getCachedWebInfo();

            if (webInfo == null) {
                log.info("缓存中未找到网站信息，从数据库重新加载");
                // 缓存为空，从数据库重新加载
                LambdaQueryChainWrapper<WebInfo> wrapper = new LambdaQueryChainWrapper<>(webInfoMapper);
                List<WebInfo> list = wrapper.list();
                if (!CollectionUtils.isEmpty(list)) {
                    webInfo = list.get(0);
                    cacheService.cacheWebInfo(webInfo);
                    log.info("从数据库重新加载网站信息并缓存 - webName: {}, webTitle: {}",
                            webInfo.getWebName(), webInfo.getWebTitle());
                } else {
                    log.error("数据库中未找到网站信息");
                    return PoetryResult.fail("网站信息不存在");
                }
            }

            // 返回完整信息（包含randomAvatar, randomName, waifuJson等配置）
            return PoetryResult.success(webInfo);
        } catch (Exception e) {
            log.error("获取管理员网站详细信息失败", e);
            return PoetryResult.fail("获取网站信息失败，请稍后重试");
        }
    }

    /**
     * 刷新管理员缓存
     */
    @PostMapping("/webInfo/refreshCache")
    @LoginCheck(0)
    public PoetryResult<Map<String, Object>> refreshAdminCache() {
        try {
            // 清理网站信息缓存
            cacheService.evictWebInfo();

            // 重新加载并缓存
            LambdaQueryChainWrapper<WebInfo> wrapper = new LambdaQueryChainWrapper<>(webInfoMapper);
            List<WebInfo> list = wrapper.list();
            if (!CollectionUtils.isEmpty(list)) {
                cacheService.cacheWebInfo(list.get(0));
                log.info("网站信息缓存刷新成功");
            }

            Map<String, Object> result = new HashMap<>();
            result.put("cleared_count", 1);
            result.put("message", "缓存刷新成功");

            log.info("管理员缓存刷新完成");
            return PoetryResult.success(result);
        } catch (Exception e) {
            log.error("刷新管理员缓存失败", e);
            return PoetryResult.fail("缓存刷新失败，请稍后重试");
        }
    }

    /**
     * 手动拉黑IP（管理员权限）
     * <p>写入 SecurityFilter 黑名单键，由 SecurityFilter 在请求入口最早期拦截，
     * 返回 403 + "403 Forbidden - IP Blacklisted"。即使客户端不执行 JS（爬虫/抓取器），
     * HTTP 请求到达服务器第一刻即被拒绝，连 HTML 源码都不会输出。
     *
     * <p>请求体字段：
     * <ul>
     *     <li>ip - 必填，目标IP</li>
     *     <li>reason - 选填，拉黑原因（写入 Redis value 便于审计）</li>
     *     <li>durationSeconds - 选填，拉黑时长（秒）：
     *         <ul>
     *           <li>&gt;0：按指定秒数拉黑（如 3600=1h, 86400=1d, 604800=7d, 2592000=30d）</li>
     *           <li>=0 或不传：使用默认 24 小时</li>
     *           <li>&lt;0（如 -1）：永久拉黑，需手动解除才失效</li>
     *         </ul>
     *     </li>
     * </ul>
     */
    @PostMapping("/security/blockIp")
    @LoginCheck(0)
    public PoetryResult<Map<String, Object>> blockIp(@RequestBody Map<String, Object> request) {
        try {
            Object ipObj = request.get("ip");
            if (ipObj == null) {
                return PoetryResult.fail("IP地址不能为空");
            }
            String ip = String.valueOf(ipObj).trim();
            if (ip.isEmpty()) {
                return PoetryResult.fail("IP地址不能为空");
            }
            if (!IpUtil.isValidIpLiteral(ip)) {
                return PoetryResult.fail("IP地址格式不正确");
            }

            String reason = request.get("reason") == null
                    ? null
                    : String.valueOf(request.get("reason")).trim();
            if (reason != null && reason.length() > 200) {
                return PoetryResult.fail("reason不能超过200字符");
            }

            long durationSeconds = 0L;
            Object durationObj = request.get("durationSeconds");
            if (durationObj instanceof Number) {
                durationSeconds = ((Number) durationObj).longValue();
            } else if (durationObj != null) {
                try {
                    durationSeconds = Long.parseLong(String.valueOf(durationObj));
                } catch (NumberFormatException e) {
                    return PoetryResult.fail("durationSeconds格式不正确");
                }
            }

            boolean permanent = durationSeconds < 0;
            boolean alreadyBlocked = cacheService.isIPBlacklisted(ip);
            boolean ok = cacheService.blacklistIP(ip, reason, durationSeconds);
            if (!ok) {
                return PoetryResult.fail("拉黑IP失败，请检查日志");
            }

            long effectiveDuration;
            String durationLabel;
            if (permanent) {
                effectiveDuration = -1L;
                durationLabel = "永久";
            } else if (durationSeconds == 0) {
                effectiveDuration = com.ld.poetry.constants.CacheConstants.IP_BLACKLIST_EXPIRE_TIME;
                durationLabel = effectiveDuration + "秒（默认24小时）";
            } else {
                effectiveDuration = durationSeconds;
                durationLabel = durationSeconds + "秒";
            }

            Map<String, Object> result = new HashMap<>();
            result.put("ip", ip);
            result.put("alreadyBlocked", alreadyBlocked);
            result.put("permanent", permanent);
            result.put("durationSeconds", effectiveDuration);
            result.put("durationLabel", durationLabel);

            log.info("管理员拉黑IP成功: ip={}, reason={}, permanent={}, durationSeconds={}, alreadyBlocked={}",
                    ip, reason, permanent, effectiveDuration, alreadyBlocked);
            return PoetryResult.success(result);
        } catch (Exception e) {
            log.error("拉黑IP失败", e);
            return PoetryResult.fail("拉黑IP失败: " + e.getMessage());
        }
    }

    /**
     * 查询 SecurityFilter 黑名单列表（管理员权限）
     * <p>返回 {@code poetize:security:blacklist:*} 所有条目，含 ip、原因、剩余秒数（ttl=-1 表示永久）。
     * <p>此接口与 {@code /captcha/getBlockedIps} 是两套独立机制：
     * <ul>
     *     <li>本接口：SecurityFilter 黑名单（24h 默认/永久），拦截所有请求 → 403</li>
     *     <li>captcha 接口：验证码侧封禁（30 分钟），仅拦截验证码流程</li>
     * </ul>
     */
    @GetMapping("/security/blacklist")
    @LoginCheck(0)
    public PoetryResult<List<Map<String, Object>>> listBlacklist() {
        try {
            List<Map<String, Object>> list = cacheService.listBlacklistedIps();
            return PoetryResult.success(list);
        } catch (Exception e) {
            log.error("查询IP黑名单列表失败", e);
            return PoetryResult.fail("查询黑名单失败: " + e.getMessage());
        }
    }

    /**
     * 手动解除 IP 拉黑（管理员权限）
     * <p>同时清除 SecurityFilter 黑名单键和攻击计数键，使该 IP 立即恢复访问。
     */
    @PostMapping("/security/unblockIp")
    @LoginCheck(0)
    public PoetryResult<Map<String, Object>> unblockIp(@RequestBody Map<String, String> request) {
        try {
            String ip = request == null ? null : request.get("ip");
            if (ip == null || ip.trim().isEmpty()) {
                return PoetryResult.fail("IP地址不能为空");
            }
            ip = ip.trim();

            boolean existed = cacheService.isIPBlacklisted(ip);
            boolean ok = cacheService.unblacklistIP(ip);

            Map<String, Object> result = new HashMap<>();
            result.put("ip", ip);
            result.put("existed", existed);
            result.put("success", ok);

            if (ok) {
                log.info("管理员解除IP拉黑: {}, existed={}", ip, existed);
                return PoetryResult.success(result);
            } else {
                return PoetryResult.fail("解除失败，请检查日志");
            }
        } catch (Exception e) {
            log.error("解除IP拉黑失败", e);
            return PoetryResult.fail("解除封禁失败: " + e.getMessage());
        }
    }

    /**
     * 添加扩展封禁规则（UA/CIDR/Region，管理员权限）。
     * <p>请求体字段：
     * <ul>
     *     <li>type - 必填，ua/cidr/region</li>
     *     <li>value - 必填，UA 文本/CIDR/地区名</li>
     *     <li>matchMode - ua 可选，contains/equals（默认 contains）</li>
     *     <li>regionType - region 必填，country/province</li>
     *     <li>reason - 选填，封禁原因</li>
     *     <li>durationSeconds - 选填，封禁时长（秒）：&gt;0 指定秒数，==0 默认 24h，&lt;0 永久</li>
     * </ul>
     */
    @PostMapping("/security/blockRule")
    @LoginCheck(0)
    public PoetryResult<Map<String, Object>> blockRule(@RequestBody Map<String, Object> request) {
        try {
            Object typeObj = request.get("type");
            if (typeObj == null) {
                return PoetryResult.fail("type不能为空");
            }
            String type = String.valueOf(typeObj).trim().toLowerCase(Locale.ROOT);
            if (!type.equals("ua") && !type.equals("cidr") && !type.equals("region")) {
                return PoetryResult.fail("type必须为 ua/cidr/region");
            }

            Object valueObj = request.get("value");
            if (valueObj == null) {
                return PoetryResult.fail("value不能为空");
            }
            String value = String.valueOf(valueObj).trim();
            if (value.isEmpty()) {
                return PoetryResult.fail("value不能为空");
            }

            // CIDR 格式校验（复用 IpUtil）
            if (type.equals("cidr") && !IpUtil.isValidIpWhitelistEntry(value)) {
                return PoetryResult.fail("CIDR格式不正确");
            }

            String matchMode = null;
            if (type.equals("ua")) {
                Object mm = request.get("matchMode");
                matchMode = mm == null ? "contains" : String.valueOf(mm).trim().toLowerCase(Locale.ROOT);
                if (matchMode.isEmpty()) {
                    matchMode = "contains";
                }
                if (!matchMode.equals("contains") && !matchMode.equals("equals")) {
                    return PoetryResult.fail("matchMode必须为 contains 或 equals");
                }
            }

            String regionType = null;
            if (type.equals("region")) {
                Object rt = request.get("regionType");
                if (rt == null) {
                    return PoetryResult.fail("regionType不能为空");
                }
                regionType = String.valueOf(rt).trim().toLowerCase(Locale.ROOT);
                if (!regionType.equals("country") && !regionType.equals("province")) {
                    return PoetryResult.fail("regionType必须为 country 或 province");
                }
                // 标准化地区名：把用户输入（"美国"/"US"/"中华人民共和国"/"江苏省"）
                // 校准为 xdb 实际返回值，与 Nginx 端 ban_check.lua 精确匹配口径对齐
                com.ld.poetry.utils.BanRegionNormalizer.NormalizeResult nr =
                        banRegionNormalizer.normalize(value, regionType);
                if (!nr.isSuccess()) {
                    String msg = nr.getErrorMessage();
                    List<String> suggestions = nr.getSuggestions();
                    if (!suggestions.isEmpty()) {
                        msg += "。可选值参考: " + String.join(", ", suggestions);
                    }
                    return PoetryResult.fail(msg);
                }
                value = nr.getNormalizedValue();
            }

            String reason = request.get("reason") == null
                    ? null
                    : String.valueOf(request.get("reason")).trim();
            if (reason != null && reason.length() > 200) {
                return PoetryResult.fail("reason不能超过200字符");
            }

            long durationSeconds = 0L;
            Object durationObj = request.get("durationSeconds");
            if (durationObj instanceof Number) {
                durationSeconds = ((Number) durationObj).longValue();
            } else if (durationObj != null) {
                try {
                    durationSeconds = Long.parseLong(String.valueOf(durationObj));
                } catch (NumberFormatException e) {
                    return PoetryResult.fail("durationSeconds格式不正确");
                }
            }

            Map<String, Object> result = cacheService.addBanRule(type, value, matchMode, regionType, reason, durationSeconds);
            if (result == null) {
                return PoetryResult.fail("添加封禁规则失败，请检查日志");
            }

            log.info("管理员添加封禁规则: type={}, value={}, matchMode={}, regionType={}, reason={}, result={}",
                    type, value, matchMode, regionType, reason, result);
            return PoetryResult.success(result);
        } catch (Exception e) {
            log.error("添加封禁规则失败", e);
            return PoetryResult.fail("添加封禁规则失败: " + e.getMessage());
        }
    }

    /**
     * 查询扩展封禁规则列表（管理员权限）。
     * @param type ua/cidr/region
     */
    @GetMapping("/security/rules")
    @LoginCheck(0)
    public PoetryResult<List<Map<String, Object>>> listRules(@RequestParam("type") String type) {
        try {
            if (type == null || type.trim().isEmpty()) {
                return PoetryResult.fail("type不能为空");
            }
            String normalizedType = type.trim().toLowerCase(Locale.ROOT);
            if (!normalizedType.equals("ua") && !normalizedType.equals("cidr") && !normalizedType.equals("region")) {
                return PoetryResult.fail("type必须为 ua/cidr/region");
            }
            // includeBuiltin=true：UA 列表追加内置 AI 爬虫硬名单（供管理端展示与删除）
            List<Map<String, Object>> list = cacheService.listBanRules(normalizedType, true);
            return PoetryResult.success(list);
        } catch (Exception e) {
            log.error("查询封禁规则列表失败", e);
            return PoetryResult.fail("查询封禁规则失败: " + e.getMessage());
        }
    }

    /**
     * 解除扩展封禁规则（管理员权限）。
     * <p>请求体字段：type（ua/cidr/region）、id（规则ID）
     */
    @PostMapping("/security/unblockRule")
    @LoginCheck(0)
    public PoetryResult<Map<String, Object>> unblockRule(@RequestBody Map<String, Object> request) {
        try {
            Object typeObj = request.get("type");
            Object idObj = request.get("id");
            if (typeObj == null || idObj == null) {
                return PoetryResult.fail("type和id不能为空");
            }
            String type = String.valueOf(typeObj).trim();
            String id = String.valueOf(idObj).trim();
            if (type.isEmpty() || id.isEmpty()) {
                return PoetryResult.fail("type和id不能为空");
            }

            boolean ok = cacheService.removeBanRule(type, id);
            Map<String, Object> result = new HashMap<>();
            result.put("type", type);
            result.put("id", id);
            result.put("success", ok);

            if (ok) {
                log.info("管理员解除封禁规则: type={}, id={}", type, id);
                return PoetryResult.success(result);
            } else {
                return PoetryResult.fail("解除失败，请检查日志");
            }
        } catch (Exception e) {
            log.error("解除封禁规则失败", e);
            return PoetryResult.fail("解除封禁失败: " + e.getMessage());
        }
    }

    /**
     * 更新看板娘状态
     */
    @PostMapping("/webInfo/updateWaifuStatus")
    @LoginCheck(0)
    public PoetryResult<Map<String, Object>> updateWaifuStatus(@RequestBody Map<String, Object> request) {
        try {
            // 验证请求参数
            if (!request.containsKey("enableWaifu")) {
                return PoetryResult.fail("缺少enableWaifu字段");
            }

            Boolean enableWaifu = (Boolean) request.get("enableWaifu");
            Integer id = (Integer) request.get("id");

            log.info("收到更新看板娘状态请求: enableWaifu={}, id={}", enableWaifu, id);

            // 获取当前网站信息
            LambdaQueryChainWrapper<WebInfo> wrapper = new LambdaQueryChainWrapper<>(webInfoMapper);
            List<WebInfo> list = wrapper.list();

            if (CollectionUtils.isEmpty(list)) {
                return PoetryResult.fail("网站信息不存在");
            }

            WebInfo webInfo = list.get(0);

            // 如果提供了id，验证id是否匹配
            if (id != null && !id.equals(webInfo.getId())) {
                return PoetryResult.fail("网站信息ID不匹配");
            }

            // 更新看板娘状态
            webInfo.setEnableWaifu(enableWaifu);

            // 保存到数据库
            webInfoService.updateById(webInfo);

            // 清理并重新缓存
            cacheService.evictWebInfo();
            cacheService.cacheWebInfo(webInfo);

            log.info("看板娘状态更新成功: enableWaifu={}", enableWaifu);

            // 返回结果
            Map<String, Object> result = new HashMap<>();
            result.put("enableWaifu", enableWaifu);
            result.put("id", webInfo.getId());

            return PoetryResult.success(result);
        } catch (Exception e) {
            log.error("更新看板娘状态失败", e);
            return PoetryResult.fail("更新看板娘状态失败，请稍后重试");
        }
    }
}
