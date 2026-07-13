package com.ld.poetry.controller;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.aop.RateLimit;
import com.ld.poetry.aop.RateLimits;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.entity.SysConfig;
import com.ld.poetry.enums.PoetryEnum;
import com.ld.poetry.service.FontSubsetService;
import com.ld.poetry.service.SysConfigService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 字体子集化管理 — REST API
 * <p>
 * 提供 TTF 字体上传、在线切分为 WOFF2 子集、状态查询和清理功能。
 * 仅站长可操作。
 */
@RestController
@RequestMapping("/fontSubset")
@Slf4j
public class FontSubsetController {

    @Autowired
    private FontSubsetService fontSubsetService;

    @Autowired
    private SysConfigService sysConfigService;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 字体资源版本号配置键：每次成功上传字体都会更新，
     * 用于给字体静态文件追加缓存失效参数 ?v=，使重新上传后浏览器自动拉取新字体。
     */
    private static final String FONT_ASSET_VERSION_KEY = "font.asset.version";

    /**
     * 上传 TTF 字体文件并执行子集化
     * 使用 cn-font-split 生成 font.css + 多个细粒度 woff2 分片
     */
    @PostMapping("/upload")
    @LoginCheck(0)
    public PoetryResult<Map<String, Object>> uploadAndSubset(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return PoetryResult.fail("请选择字体文件");
        }

        String filename = file.getOriginalFilename();
        if (filename == null
                || (!filename.toLowerCase().endsWith(".ttf") && !filename.toLowerCase().endsWith(".otf"))) {
            return PoetryResult.fail("仅支持 .ttf 或 .otf 格式的字体文件");
        }

        try {
            byte[] ttfData = file.getBytes();
            Path outputDir = fontSubsetService.getDefaultOutputDir();

            log.info("开始字体子集化: 文件={}, 大小={} bytes, 输出目录={}",
                    filename, ttfData.length, outputDir);

            Map<String, Object> result = fontSubsetService.subsetFont(ttfData, outputDir);
            // 将本次生成的资源版本号写入系统配置，前端据此给字体静态文件追加 ?v= 缓存失效参数。
            // 版本号写入失败不应阻塞上传成功返回（此时字体文件已成功生成），仅记录日志。
            Object versionObj = result.get("version");
            if (versionObj != null) {
                try {
                    upsertPublicConfig(FONT_ASSET_VERSION_KEY, "字体资源版本号(缓存失效)", versionObj.toString());
                } catch (Exception ex) {
                    log.warn("写入字体资源版本号失败，前台可能需要硬刷新才能看到新字体", ex);
                }
            }
            return PoetryResult.success(result);
        } catch (Exception e) {
            log.error("字体子集化失败", e);
            return PoetryResult.fail("字体子集化失败: " + e.getMessage());
        }
    }

    /**
     * 查询当前字体文件状态
     */
    @GetMapping("/status")
    @LoginCheck(0)
    public PoetryResult<Map<String, Object>> getStatus() {
        try {
            Map<String, Object> status = fontSubsetService.getStatus();
            return PoetryResult.success(status);
        } catch (Exception e) {
            log.error("查询字体状态失败", e);
            return PoetryResult.fail("查询字体状态失败: " + e.getMessage());
        }
    }

    /**
     * 清理已生成的字体子集文件
     */
    @DeleteMapping("/clean")
    @LoginCheck(0)
    public PoetryResult<String> cleanSubsets() {
        try {
            boolean success = fontSubsetService.cleanSubsets();
            if (success) {
                // 清理后重置版本号，前端不再追加 ?v=，回退到默认内置分片
                try {
                    upsertPublicConfig(FONT_ASSET_VERSION_KEY, "字体资源版本号(缓存失效)", "");
                } catch (Exception e) {
                    log.warn("清理后重置字体资源版本号失败，但不影响清理结果", e);
                }
                return PoetryResult.success("字体子集文件已清理");
            } else {
                return PoetryResult.fail("部分文件清理失败，请检查日志");
            }
        } catch (Exception e) {
            log.error("清理字体子集失败", e);
            return PoetryResult.fail("清理失败: " + e.getMessage());
        }
    }

    /**
     * 下载字体切割包为 ZIP（font.css + 全部 woff2 分片）。
     *
     * <p>流式写入 {@link HttpServletResponse} 输出流，绕过 HttpMessageConverter 路径。
     * 配合 font.cdn.base-url 系统配置，将字体分片下载后上传至 CDN 加载。
     */
    @GetMapping("/download")
    @LoginCheck(0)
    @RateLimits({
            // 站长维度：单个账号每 5 分钟最多下载 10 次，防止 token 泄露后被反复触发打包
            @RateLimit(name = "fontDownload:user", count = 10, time = 300,
                    keyType = RateLimit.KeyType.USER, message = "字体下载请求过于频繁，请5分钟后再试"),
            // IP 维度：单 IP 每分钟最多 30 次，作为洪水请求的兜底防护
            @RateLimit(name = "fontDownload:ip", count = 30, time = 60,
                    keyType = RateLimit.KeyType.IP, message = "当前网络字体下载请求过多，请稍后再试")
    })
    public void downloadFontPackage(HttpServletResponse response) {
        Path outputDir = fontSubsetService.getEffectiveOutputDir();

        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"font_chunks.zip\"");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
        response.setDateHeader(HttpHeaders.EXPIRES, 0);

        // 优先发送预打包的缓存 ZIP（秒级零拷贝），避免下载时实时打包数百个分片导致前端超时。
        // 缓存由字体切割完成时预生成（buildCachedZip），清理时同步删除。
        Path cachedZip = fontSubsetService.getCachedZipPath(outputDir);
        // 若有后台预打包正在进行（上传后立即下载的常见场景），等待它完成再发缓存，
        // 避免回退到慢速实时打包。无进行中的任务时立即返回，零开销。
        fontSubsetService.awaitCacheReady(outputDir, 30);
        try {
            long zipSize = Files.size(cachedZip);
            if (zipSize > 0) {
                response.setContentLengthLong(zipSize);
                Files.copy(cachedZip, response.getOutputStream());
                response.getOutputStream().flush();
                return;
            }
        } catch (IOException e) {
            if (response.isCommitted()) {
                log.error("发送缓存字体切割包失败（响应已提交）", e);
                return;
            }
            log.warn("缓存字体切割包不可用或发送失败，回退到实时打包", e);
        }

        // 回退：缓存不存在时实时收集文件并打包（首次下载内置字体或预打包失败时走到这里）
        List<Path> files;
        try {
            files = fontSubsetService.collectFontChunkFiles(outputDir);
        } catch (IOException e) {
            writeJsonError(response, HttpStatus.BAD_REQUEST,
                    e.getMessage() != null ? e.getMessage() : "字体分片目录为空，请先上传字体进行切片");
            return;
        }

        OutputStream out = null;
        try {
            out = response.getOutputStream();
            fontSubsetService.writeZip(outputDir, files, out);
            out.flush();
        } catch (IOException e) {
            if (!response.isCommitted()) {
                log.warn("流式打包字体切割包失败，将返回错误响应", e);
                writeJsonError(response, HttpStatus.INTERNAL_SERVER_ERROR, "字体切割包打包失败");
            } else {
                log.error("流式打包字体切割包失败（响应已提交）", e);
            }
        }
    }

    private void writeJsonError(HttpServletResponse response, HttpStatus status, String message) {
        try {
            if (response.isCommitted()) {
                log.warn("响应已提交，无法返回下载错误 JSON: {}", message);
                return;
            }
            // 重置已设置的下载头与缓冲字节，随后写入 JSON 错误响应
            response.reset();
            response.setStatus(status.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            // 以字节方式写入 JSON 响应体
            OutputStream out = response.getOutputStream();
            out.write(OBJECT_MAPPER.writeValueAsBytes(PoetryResult.fail(message)));
            out.flush();
        } catch (Exception e) {
            log.error("写入字体下载错误信息失败: {}", message, e);
        }
    }

    /**
     * 按 configKey 写入/更新一条「公开」系统配置（不存在则新增，存在则覆盖）。
     */
    private void upsertPublicConfig(String configKey, String configName, String configValue) {
        LambdaQueryChainWrapper<SysConfig> wrapper = new LambdaQueryChainWrapper<>(sysConfigService.getBaseMapper());
        SysConfig existing = wrapper
                .eq(SysConfig::getConfigKey, configKey)
                .eq(SysConfig::getConfigType, String.valueOf(PoetryEnum.SYS_CONFIG_PUBLIC.getCode()))
                .one();
        SysConfig config = existing != null ? existing : new SysConfig();
        config.setConfigKey(configKey);
        config.setConfigName(configName);
        config.setConfigType(String.valueOf(PoetryEnum.SYS_CONFIG_PUBLIC.getCode()));
        config.setConfigValue(configValue);
        sysConfigService.saveOrUpdate(config);
    }
}
