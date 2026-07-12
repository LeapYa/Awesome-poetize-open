package com.ld.poetry.controller;

import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.service.FontSubsetService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

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
     * 下载字体切割包（font.css + 全部 woff2 分片打包为 ZIP）
     * <p>
     * 用于将切好的字体分片下载后上传至 CDN，配合系统配置 font.cdn.base-url 实现外部 CDN 加载。
     */
    @GetMapping("/download")
    @LoginCheck(0)
    public ResponseEntity<?> downloadFontPackage() {
        Path outputDir = fontSubsetService.getDefaultOutputDir();
        if (!Files.exists(outputDir) || isDirEmpty(outputDir)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(PoetryResult.fail("字体分片目录为空，请先上传字体进行切片"));
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"font_chunks.zip\"");
        return ResponseEntity.ok()
                .headers(headers)
                .body((StreamingResponseBody) outputStream -> {
                    try {
                        fontSubsetService.zipFontChunks(outputStream);
                    } catch (IOException e) {
                        log.error("流式打包字体切割包失败", e);
                        throw new UncheckedIOException(e);
                    }
                });
    }

    private boolean isDirEmpty(Path dir) {
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.findAny().isEmpty();
        } catch (IOException e) {
            log.warn("无法读取字体分片目录: {}", dir, e);
            return true;
        }
    }
}
