package com.ld.poetry.controller;

import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.controller.dto.RagPreviewRequest;
import com.ld.poetry.entity.SysAiConfig;
import com.ld.poetry.service.SysAiConfigService;
import com.ld.poetry.service.ai.rag.RagSyncService;
import com.ld.poetry.utils.DockerNetworkUtil;
import com.ld.poetry.utils.PoetryUtil;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
// Swagger注解已禁用，改为普通注释
// import io.swagger.v3.oas.annotations.Operation;
// import io.swagger.v3.oas.annotations.Parameter;
// import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI配置管理控制器
 * 提供AI聊天、翻译、API配置的统一管理接口
 * 
 * @author LeapYa
 * @since 2025-10-18
 */
@Slf4j
@RestController
@RequestMapping("/webInfo/ai/config")
@RequiredArgsConstructor
// AI配置管理 - AI聊天、翻译、API配置的统一管理
public class SysAiConfigController {

    private final SysAiConfigService sysAiConfigService;
    private final RagSyncService ragSyncService;

    // ========== AI聊天配置接口 ==========

    /**
     * 获取AI聊天配置（前端用，API密钥已脱敏）
     */
    @GetMapping("/chat/get")
    @LoginCheck(0)
    public PoetryResult<SysAiConfig> getAiChatConfig(
            @RequestParam(defaultValue = "default") String configName) {
        
        SysAiConfig config = sysAiConfigService.getAiChatConfig(configName);
        
        if (config == null) {
            return PoetryResult.fail("配置不存在");
        }
        
        return PoetryResult.success(config);
    }

    /**
     * 获取AI聊天流式配置（前端用，用于初始化聊天界面）
     * 不需要登录即可访问，返回简化的配置信息
     */
    @GetMapping("/chat/getStreamingConfig")
    public PoetryResult<Map<String, Object>> getStreamingConfig(
            @RequestParam(defaultValue = "default") String configName) {
        
        Map<String, Object> result = sysAiConfigService.getStreamingConfig(configName);
        return PoetryResult.success(result);
    }

    /**
     * 获取AI聊天配置（内部服务用，API密钥完整未脱敏）
     * 仅供Python等内部服务调用，不对外开放
     * 安全验证：IP地址必须在Docker内部网络 + 请求头验证
     */
    @GetMapping("/chat/getInternal")
    public PoetryResult<SysAiConfig> getAiChatConfigInternal(
            @RequestParam(defaultValue = "default") String configName,
            @RequestHeader(value = "X-Internal-Service", required = false) String internalService,
            HttpServletRequest request) {
        
        // 双重安全检查
        String clientIp = PoetryUtil.getIpAddr(request);
        
        // 1. 验证IP是否在允许范围（Docker内网 + 本地环回）
        boolean isLocalhost = "127.0.0.1".equals(clientIp) || "localhost".equals(clientIp) || "::1".equals(clientIp);
        boolean isInDockerNetwork = DockerNetworkUtil.isInDockerNetwork(clientIp);
        
        if (!isLocalhost && !isInDockerNetwork) {
            log.warn("拒绝非内部网络的getInternal请求，IP: {}", clientIp);
            return PoetryResult.fail("权限不足");
        }
        
        // 2. 验证服务名是否受信任
        if (!DockerNetworkUtil.isTrustedService(internalService)) {
            log.warn("拒绝非受信任服务的getInternal请求，服务名: {}, IP: {}", internalService, clientIp);
            return PoetryResult.fail("权限不足");
        }
        
        log.info("内部服务请求验证通过: 服务={}, IP={}", internalService, clientIp);
        
        SysAiConfig config = sysAiConfigService.getAiChatConfigInternal(configName);
        
        if (config == null) {
            return PoetryResult.fail("配置不存在");
        }
        
        return PoetryResult.success(config);
    }

    /**
     * 保存AI聊天配置
     */
    @PostMapping("/chat/save")
    @LoginCheck(0)
    public PoetryResult<Boolean> saveAiChatConfig(@RequestBody SysAiConfig config) {
        try {
            boolean success = sysAiConfigService.saveAiChatConfig(config);
            
            if (success) {
                return PoetryResult.success();
            } else {
                return PoetryResult.fail("保存失败");
            }
            
        } catch (Exception e) {
            log.error("保存AI聊天配置失败: {}", e.getMessage(), e);
            return PoetryResult.fail("保存失败: " + e.getMessage());
        }
    }

    /**
     * 测试AI聊天连接
     */
    @PostMapping("/chat/test")
    @LoginCheck(0)
    public PoetryResult<Map<String, Object>> testAiChatConnection(@RequestBody SysAiConfig config) {
        try {
            Map<String, Object> result = sysAiConfigService.testConnection(resolveAiChatTestConfig(config));
            return PoetryResult.success(result);
            
        } catch (Exception e) {
            log.error("测试AI聊天连接失败: {}", e.getMessage(), e);
            return PoetryResult.fail("测试失败: " + e.getMessage());
        }
    }

    /**
     * 切换AI聊天启用状态
     */
    @PostMapping("/chat/toggle")
    @LoginCheck(0)
    public PoetryResult<Boolean> toggleAiChatEnabled(@RequestParam Integer id) {
        
        boolean success = sysAiConfigService.toggleEnabled(id);
        
        if (success) {
            return PoetryResult.success();
        } else {
            return PoetryResult.fail("切换失败");
        }
    }

    @GetMapping("/chat/rag/status")
    @LoginCheck(0)
    public PoetryResult<Map<String, Object>> getAiChatRagStatus() {
        return PoetryResult.success(ragSyncService.getStatus());
    }

    @PostMapping("/chat/rag/rebuild")
    @LoginCheck(0)
    public PoetryResult<Boolean> rebuildAiChatRag() {
        String blockingReason = ragSyncService.getBlockingReason();
        if (StringUtils.hasText(blockingReason)) {
            return PoetryResult.fail(blockingReason);
        }
        ragSyncService.rebuildAllAsync();
        return PoetryResult.success();
    }

    @PostMapping("/chat/rag/preview")
    @LoginCheck(0)
    public PoetryResult<Map<String, Object>> previewAiChatRag(@Valid @RequestBody RagPreviewRequest request) {
        String blockingReason = ragSyncService.getBlockingReason();
        if (StringUtils.hasText(blockingReason)) {
            return PoetryResult.fail(blockingReason);
        }
        return PoetryResult.success(ragSyncService.preview(request.query(), request.pageContext()));
    }

    private SysAiConfig resolveAiChatTestConfig(SysAiConfig config) {
        SysAiConfig resolvedConfig = config != null ? config : new SysAiConfig();
        resolvedConfig.setConfigType("ai_chat");

        if (!StringUtils.hasText(resolvedConfig.getConfigName())) {
            resolvedConfig.setConfigName("default");
        }

        SysAiConfig savedConfig = sysAiConfigService.getAiChatConfigInternal(resolvedConfig.getConfigName());
        if (savedConfig == null) {
            return resolvedConfig;
        }

        if (!StringUtils.hasText(resolvedConfig.getProvider())) {
            resolvedConfig.setProvider(savedConfig.getProvider());
        }

        if (!StringUtils.hasText(resolvedConfig.getApiBase())) {
            resolvedConfig.setApiBase(savedConfig.getApiBase());
        }

        if (!StringUtils.hasText(resolvedConfig.getModel())) {
            resolvedConfig.setModel(savedConfig.getModel());
        }

        if (!StringUtils.hasText(resolvedConfig.getApiKey()) || resolvedConfig.getApiKey().contains("*")) {
            resolvedConfig.setApiKey(savedConfig.getApiKey());
        }

        if (resolvedConfig.getTemperature() == null) {
            resolvedConfig.setTemperature(savedConfig.getTemperature());
        }

        if (resolvedConfig.getEnableThinking() == null) {
            resolvedConfig.setEnableThinking(savedConfig.getEnableThinking());
        }

        if (!StringUtils.hasText(resolvedConfig.getReasoningEffort())) {
            resolvedConfig.setReasoningEffort(savedConfig.getReasoningEffort());
        }

        if (!StringUtils.hasText(resolvedConfig.getExtraConfig())) {
            resolvedConfig.setExtraConfig(savedConfig.getExtraConfig());
        }

        return resolvedConfig;
    }

    // ========== 文章AI助手配置接口 ==========

    /**
     * 获取文章AI助手配置（前端用，API密钥已脱敏）
     */
    @GetMapping("/articleAi/get")
    @LoginCheck(0)
    public PoetryResult<SysAiConfig> getArticleAiConfig(
            @RequestParam(defaultValue = "default") String configName) {
        
        SysAiConfig config = sysAiConfigService.getArticleAiConfig(configName);
        
        if (config == null) {
            return PoetryResult.fail("配置不存在");
        }
        
        return PoetryResult.success(config);
    }

    /**
     * 获取文章AI助手配置（内部服务用，API密钥完整未脱敏）
     * 仅供Python等内部服务调用
     * 安全验证：IP地址必须在Docker内部网络 + 请求头验证
     */
    @GetMapping("/articleAi/getInternal")
    public PoetryResult<Map<String, Object>> getArticleAiConfigInternal(
            @RequestParam(defaultValue = "default") String configName,
            @RequestHeader(value = "X-Internal-Service", required = false) String internalService,
            HttpServletRequest request) {
        
        // 双重安全检查
        String clientIp = PoetryUtil.getIpAddr(request);
        
        // 1. 验证IP是否在允许范围（Docker内网 + 本地环回）
        boolean isLocalhost = "127.0.0.1".equals(clientIp) || "localhost".equals(clientIp) || "::1".equals(clientIp);
        boolean isInDockerNetwork = DockerNetworkUtil.isInDockerNetwork(clientIp);
        
        if (!isLocalhost && !isInDockerNetwork) {
            log.warn("拒绝非内部网络的getInternal请求，IP: {}", clientIp);
            return PoetryResult.fail("权限不足");
        }
        
        // 2. 验证服务名是否受信任
        if (!DockerNetworkUtil.isTrustedService(internalService)) {
            log.warn("拒绝非受信任服务的getInternal请求，服务名: {}, IP: {}", internalService, clientIp);
            return PoetryResult.fail("权限不足");
        }
        
        log.info("内部服务请求验证通过: 服务={}, IP={}", internalService, clientIp);
        
        SysAiConfig config = sysAiConfigService.getArticleAiConfigInternal(configName);
        
        if (config == null) {
            return PoetryResult.fail("配置不存在");
        }
        
        // 将JSON字符串字段解析为对象，方便Python使用
        Map<String, Object> result = sysAiConfigService.convertConfigToMap(config);
        
        return PoetryResult.success(result);
    }

    /**
     * 保存文章AI助手配置
     */
    @PostMapping("/articleAi/save")
    @LoginCheck(0)
    public PoetryResult<Boolean> saveArticleAiConfig(@RequestBody SysAiConfig config) {
        try {
            boolean success = sysAiConfigService.saveArticleAiConfig(config);
            
            if (success) {
                return PoetryResult.success();
            } else {
                return PoetryResult.fail("保存失败");
            }
            
        } catch (Exception e) {
            log.error("保存文章AI助手配置失败: {}", e.getMessage(), e);
            return PoetryResult.fail("保存失败: " + e.getMessage());
        }
    }

    /**
     * 获取默认支持的语言列表
     */
    @GetMapping("/articleAi/defaultLang")
    public PoetryResult<Map<String, Object>> getDefaultLanguages() {
        Map<String, Object> result = sysAiConfigService.getDefaultLanguages();
        return PoetryResult.success(result);
    }

    /**
     * 检查系统是否有文章（用于前端判断是否允许修改源语言）
     */
    @GetMapping("/articleAi/hasArticles")
    public PoetryResult<Boolean> checkHasArticles() {
        boolean hasArticles = sysAiConfigService.hasArticles();
        return PoetryResult.success(hasArticles);
    }

    /**
     * 获取系统语言映射配置（前台展示用，原生语言文字）
     * 返回语言代码到自然语言名称的映射
     */
    @GetMapping("/system/languageMapping")
    public PoetryResult<Map<String, String>> getLanguageMapping() {
        Map<String, String> mapping = sysAiConfigService.getLanguageMapping();
        return PoetryResult.success(mapping);
    }

    /**
     * 获取系统语言映射配置（后台管理用，中文翻译）
     * 返回语言代码到中文名称的映射，方便管理员理解
     */
    @GetMapping("/system/languageMappingAdmin")
    public PoetryResult<Map<String, String>> getLanguageMappingAdmin() {
        Map<String, String> mapping = sysAiConfigService.getLanguageMappingAdmin();
        return PoetryResult.success(mapping);
    }

    /**
     * 测试文章AI助手连接
     */
    @PostMapping("/articleAi/test")
    @LoginCheck(0)
    public PoetryResult<Map<String, Object>> testArticleAiConnection(@RequestBody SysAiConfig config) {
        try {
            Map<String, Object> result = sysAiConfigService.testConnection(config);
            return PoetryResult.success(result);
            
        } catch (Exception e) {
            log.error("测试文章AI助手连接失败: {}", e.getMessage(), e);
            return PoetryResult.fail("测试失败: " + e.getMessage());
        }
    }

    // ========== AI API配置接口 ==========

    /**
     * 获取AI API配置
     */
    @GetMapping("/api/get")
    @LoginCheck(0)
    public PoetryResult<SysAiConfig> getAiApiConfig(
            @RequestParam(defaultValue = "default") String configName) {
        
        SysAiConfig config = sysAiConfigService.getAiApiConfig(configName);
        
        if (config == null) {
            return PoetryResult.fail("配置不存在");
        }
        
        return PoetryResult.success(config);
    }

    /**
     * 保存AI API配置
     */
    @PostMapping("/api/save")
    @LoginCheck(0)
    public PoetryResult<Boolean> saveAiApiConfig(@RequestBody SysAiConfig config) {
        try {
            boolean success = sysAiConfigService.saveAiApiConfig(config);
            
            if (success) {
                return PoetryResult.success();
            } else {
                return PoetryResult.fail("保存失败");
            }
            
        } catch (Exception e) {
            log.error("保存AI API配置失败: {}", e.getMessage(), e);
            return PoetryResult.fail("保存失败: " + e.getMessage());
        }
    }

    /**
     * 测试AI API连接
     */
    @PostMapping("/api/test")
    @LoginCheck(0)
    public PoetryResult<Map<String, Object>> testAiApiConnection(@RequestBody SysAiConfig config) {
        try {
            Map<String, Object> result = sysAiConfigService.testConnection(config);
            return PoetryResult.success(result);
            
        } catch (Exception e) {
            log.error("测试AI API连接失败: {}", e.getMessage(), e);
            return PoetryResult.fail("测试失败: " + e.getMessage());
        }
    }

    // ========== 通用接口 ==========

    /**
     * 获取所有AI配置列表
     */
    @GetMapping("/list")
    @LoginCheck(0)
    public PoetryResult<List<SysAiConfig>> listAllConfigs() {
        List<SysAiConfig> configs = sysAiConfigService.listAllConfigs();
        return PoetryResult.success(configs);
    }

    /**
     * 根据类型获取配置列表
     * @param configType 配置类型: ai_chat, ai_api, translation
     */
    @GetMapping("/list/{configType}")
    @LoginCheck(0)
    public PoetryResult<List<SysAiConfig>> listConfigsByType(@PathVariable String configType) {
        
        List<SysAiConfig> configs = sysAiConfigService.listConfigsByType(configType);
        return PoetryResult.success(configs);
    }

    /**
     * 删除配置
     * @param id 配置ID
     */
    @DeleteMapping("/delete/{id}")
    @LoginCheck(0)
    public PoetryResult<Boolean> deleteConfig(@PathVariable Integer id) {
        
        boolean success = sysAiConfigService.deleteConfig(id);
        
        if (success) {
            return PoetryResult.success();
        } else {
            return PoetryResult.fail("删除失败");
        }
    }

    /**
     * 切换配置启用状态
     * @param id 配置ID
     */
    @PostMapping("/toggle/{id}")
    @LoginCheck(0)
    public PoetryResult<Boolean> toggleConfigEnabled(@PathVariable Integer id) {
        
        boolean success = sysAiConfigService.toggleEnabled(id);
        
        if (success) {
            return PoetryResult.success();
        } else {
            return PoetryResult.fail("切换失败");
        }
    }
}

