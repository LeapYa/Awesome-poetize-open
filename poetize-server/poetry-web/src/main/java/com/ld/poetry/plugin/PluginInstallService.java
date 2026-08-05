package com.ld.poetry.plugin;

import tools.jackson.databind.json.JsonMapper;
import com.ld.poetry.entity.SysPlugin;
import com.ld.poetry.dao.SysPluginMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.*;

/**
 * 插件安装/卸载服务
 * <p>
 * 处理 .zip 格式的插件包：解压、校验 manifest、执行 SQL、注册前端代码。
 * 后端 Groovy 脚本源码仍存入数据库（保留兼容性），但不再编译执行（Native Image 不支持动态类加载）。
 * </p>
 *
 * @author LeapYa
 * @since 2026-02-19
 */
@Slf4j
@Service
public class PluginInstallService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SysPluginMapper sysPluginMapper;

    @Autowired
    private SqlSafetyValidator sqlSafetyValidator;

    @Autowired
    private JsonMapper objectMapper;

    /** 插件包允许的最大大小（10MB） */
    private static final long MAX_PLUGIN_SIZE = 10 * 1024 * 1024L;

    /**
     * 安装插件
     *
     * @param file 上传的 .zip 文件
     * @return 安装结果信息
     */
    @Transactional(rollbackFor = Exception.class)
    public InstallResult installPlugin(MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("插件文件不能为空");
        }
        if (file.getSize() > MAX_PLUGIN_SIZE) {
            throw new IllegalArgumentException("插件文件超过 10MB 限制");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.endsWith(".zip")) {
            throw new IllegalArgumentException("插件文件必须是 .zip 格式");
        }

        // 解压 zip，收集各文件内容
        PluginPackage pkg = extractZip(file.getInputStream());

        // 校验 manifest
        validateManifest(pkg.manifest);

        String pluginKey = pkg.manifest.getName();
        String version = pkg.manifest.getVersion();

        // 检查是否已安装
        SysPlugin existing = getPluginByKey(pluginKey);
        if (existing != null) {
            throw new IllegalStateException(
                    "插件 [" + pluginKey + "] 已安装（版本 " + existing.getVersion() + "），请先卸载或使用升级功能");
        }

        // 执行 install.sql
        if (pkg.installSql != null && !pkg.installSql.isBlank()) {
            sqlSafetyValidator.validateInstall(pkg.installSql);
            executeSql(pkg.installSql, pluginKey, version, "install.sql");
            log.info("插件 [{}] install.sql 执行成功", pluginKey);
        }

        // 构建 SysPlugin 记录
        SysPlugin plugin = new SysPlugin();
        plugin.setPluginKey(pluginKey);
        plugin.setPluginName(pkg.manifest.getDisplayName());
        plugin.setPluginDescription(pkg.manifest.getDescription());
        plugin.setPluginType(pkg.manifest.getPluginType());
        plugin.setVersion(version);
        plugin.setAuthor(pkg.manifest.getAuthor());
        plugin.setManifest(objectMapper.writeValueAsString(pkg.manifest));
        plugin.setPluginCode(pkg.frontendJs);
        plugin.setFrontendCss(pkg.frontendCss);
        plugin.setBackendCode(pkg.backendGroovy);
        plugin.setHasBackend(pkg.backendGroovy != null && !pkg.backendGroovy.isBlank() ? (byte) 1 : (byte) 0);
        plugin.setInstallSql(pkg.installSql);
        plugin.setUninstallSql(pkg.uninstallSql);
        plugin.setEnabled(true);
        plugin.setIsSystem(false);
        plugin.setSortOrder(99);
        plugin.setCreateTime(LocalDateTime.now());
        plugin.setUpdateTime(LocalDateTime.now());

        // 加载 plugin_config 默认值
        if (pkg.manifest.getConfigSchema() != null && !pkg.manifest.getConfigSchema().isEmpty()) {
            Map<String, Object> defaultConfig = new LinkedHashMap<>();
            pkg.manifest.getConfigSchema().forEach((key, field) -> {
                if (field.getDefaultValue() != null) {
                    defaultConfig.put(key, field.getDefaultValue());
                }
            });
            plugin.setPluginConfig(objectMapper.writeValueAsString(defaultConfig));
        }

        sysPluginMapper.insert(plugin);

        String warning = backendNotice(pkg);
        if (warning != null) {
            log.warn("插件 [{}] 包含后端脚本，当前版本不支持执行，仅存档源码", pluginKey);
        }
        log.info("插件 [{}] v{} 安装成功", pluginKey, version);
        return new InstallResult(true, "插件安装成功", pluginKey, version, warning);
    }

    /**
     * 卸载插件
     *
     * @param pluginKey 插件 key
     */
    @Transactional(rollbackFor = Exception.class)
    public void uninstallPlugin(String pluginKey) throws Exception {
        SysPlugin plugin = getPluginByKey(pluginKey);
        if (plugin == null) {
            throw new IllegalArgumentException("插件 [" + pluginKey + "] 未安装");
        }
        if (Boolean.TRUE.equals(plugin.getIsSystem())) {
            throw new IllegalStateException("系统内置插件不允许卸载");
        }

        // 执行 uninstall.sql
        if (plugin.getUninstallSql() != null && !plugin.getUninstallSql().isBlank()) {
            sqlSafetyValidator.validateUninstall(plugin.getUninstallSql());
            executeSql(plugin.getUninstallSql(), pluginKey, plugin.getVersion(), "uninstall.sql");
            log.info("插件 [{}] uninstall.sql 执行成功", pluginKey);
        }

        // 删除 plugin_migrations 记录
        jdbcTemplate.update("DELETE FROM plugin_migrations WHERE plugin_key = ?", pluginKey);

        // 删除数据库记录
        sysPluginMapper.deleteById(plugin.getId());

        log.info("插件 [{}] 卸载成功", pluginKey);
    }

    /**
     * 升级插件
     */
    @Transactional(rollbackFor = Exception.class)
    public InstallResult upgradePlugin(MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("插件文件不能为空");
        }
        PluginPackage pkg = extractZip(file.getInputStream());
        validateManifest(pkg.manifest);

        String pluginKey = pkg.manifest.getName();
        String newVersion = pkg.manifest.getVersion();

        SysPlugin existing = getPluginByKey(pluginKey);
        if (existing == null) {
            // 未安装过，直接安装
            return installPlugin(file);
        }

        String oldVersion = existing.getVersion();
        if (oldVersion != null && compareVersions(newVersion, oldVersion) <= 0) {
            throw new IllegalStateException(
                    "新版本 " + newVersion + " 不高于当前版本 " + oldVersion + "，无需升级");
        }

        // 执行升级 SQL
        if (pkg.upgradeSql != null && !pkg.upgradeSql.isBlank()) {
            // 检查是否已执行过
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM plugin_migrations WHERE plugin_key = ? AND version = ? AND sql_file = 'upgrade.sql'",
                    Integer.class, pluginKey, newVersion);
            if (count == null || count == 0) {
                sqlSafetyValidator.validateInstall(pkg.upgradeSql);
                executeSql(pkg.upgradeSql, pluginKey, newVersion, "upgrade.sql");
            }
        }

        // 更新记录
        existing.setVersion(newVersion);
        existing.setAuthor(pkg.manifest.getAuthor());
        existing.setPluginName(pkg.manifest.getDisplayName());
        existing.setPluginDescription(pkg.manifest.getDescription());
        existing.setManifest(objectMapper.writeValueAsString(pkg.manifest));
        existing.setPluginCode(pkg.frontendJs);
        existing.setFrontendCss(pkg.frontendCss);
        existing.setBackendCode(pkg.backendGroovy);
        existing.setHasBackend(pkg.backendGroovy != null && !pkg.backendGroovy.isBlank() ? (byte) 1 : (byte) 0);
        existing.setInstallSql(pkg.installSql);
        existing.setUninstallSql(pkg.uninstallSql);
        existing.setUpdateTime(LocalDateTime.now());
        sysPluginMapper.updateById(existing);

        String warning = backendNotice(pkg);
        if (warning != null) {
            log.warn("插件 [{}] 包含后端脚本，当前版本不支持执行，仅存档源码", pluginKey);
        }
        log.info("插件 [{}] 从 {} 升级到 {} 成功", pluginKey, oldVersion, newVersion);
        return new InstallResult(true, "插件升级成功", pluginKey, newVersion, warning);
    }

    // ============ 内部方法 ============

    /**
     * 解压 .zip 插件包，按约定路径收集各文件
     */
    private PluginPackage extractZip(InputStream inputStream) throws IOException {
        PluginPackage pkg = new PluginPackage();
        try (ZipInputStream zis = new ZipInputStream(inputStream, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (entry.isDirectory())
                    continue;

                String content = readEntry(zis);
                switch (name) {
                    case "manifest.json" -> pkg.manifestJson = content;
                    case "frontend/index.js" -> pkg.frontendJs = content;
                    case "frontend/style.css" -> pkg.frontendCss = content;
                    case "backend/main.groovy" -> pkg.backendGroovy = content;
                    case "sql/install.sql" -> pkg.installSql = content;
                    case "sql/uninstall.sql" -> pkg.uninstallSql = content;
                    case "sql/upgrade.sql", "sql/upgrade_v2.sql" -> pkg.upgradeSql = content;
                }
                zis.closeEntry();
            }
        }

        if (pkg.manifestJson == null) {
            throw new IllegalArgumentException("插件包缺少 manifest.json 文件");
        }

        try {
            JsonMapper mapper = JsonMapper.builder().build();
            pkg.manifest = mapper.readValue(pkg.manifestJson, PluginManifest.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("manifest.json 格式错误: " + e.getMessage());
        }

        return pkg;
    }

    private String readEntry(ZipInputStream zis) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int len;
        while ((len = zis.read(buf)) != -1) {
            baos.write(buf, 0, len);
        }
        return baos.toString(StandardCharsets.UTF_8);
    }

    private void validateManifest(PluginManifest manifest) {
        if (manifest.getName() == null || manifest.getName().isBlank()) {
            throw new IllegalArgumentException("manifest.json 缺少 name 字段");
        }
        if (!manifest.getName().matches("[a-z][a-z0-9\\-]*")) {
            throw new IllegalArgumentException("插件 name 只能包含小写字母、数字和连字符，且以字母开头");
        }
        if (manifest.getVersion() == null || manifest.getVersion().isBlank()) {
            throw new IllegalArgumentException("manifest.json 缺少 version 字段");
        }
        if (manifest.getPluginType() == null || manifest.getPluginType().isBlank()) {
            throw new IllegalArgumentException("manifest.json 缺少 pluginType 字段");
        }
    }

    private void executeSql(String sql, String pluginKey, String version, String sqlFile) {
        // 按分号分割执行多条语句
        String[] statements = sql.split(";");
        for (String stmt : statements) {
            String trimmed = stmt.trim();
            if (!trimmed.isEmpty()) {
                jdbcTemplate.execute(trimmed);
            }
        }
        // 记录迁移日志
        jdbcTemplate.update(
                "INSERT IGNORE INTO plugin_migrations (plugin_key, version, sql_file) VALUES (?, ?, ?)",
                pluginKey, version, sqlFile);
    }

    private SysPlugin getPluginByKey(String pluginKey) {
        LambdaQueryWrapper<SysPlugin> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysPlugin::getPluginKey, pluginKey);
        return sysPluginMapper.selectOne(wrapper);
    }

    /**
     * 插件包含后端脚本时生成警告文案（Groovy 引擎已移除，脚本仅存档不执行），无后端脚本返回 null
     */
    private String backendNotice(PluginPackage pkg) {
        if (pkg.backendGroovy != null && !pkg.backendGroovy.isBlank()) {
            return "该插件包含后端脚本（backend/main.groovy），当前版本不支持执行后端脚本，依赖后端逻辑的功能不会生效";
        }
        return null;
    }

    private int compareVersions(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        int len = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < len; i++) {
            int p1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
            int p2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;
            if (p1 != p2)
                return Integer.compare(p1, p2);
        }
        return 0;
    }

    // ============ 内部数据类 ============

    private static class PluginPackage {
        String manifestJson;
        PluginManifest manifest;
        String frontendJs;
        String frontendCss;
        String backendGroovy;
        String installSql;
        String uninstallSql;
        String upgradeSql;
    }

    /**
     * 安装/升级结果
     *
     * @param warning 非致命警告（如插件包含不再支持执行的后端脚本），无警告为 null
     */
    public record InstallResult(boolean success, String message, String pluginKey, String version, String warning) {
    }
}
