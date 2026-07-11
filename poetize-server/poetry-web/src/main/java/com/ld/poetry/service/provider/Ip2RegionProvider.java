package com.ld.poetry.service.provider;

import com.ld.poetry.utils.VisitRegionNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.lionsoul.ip2region.xdb.LongByteArray;
import org.lionsoul.ip2region.xdb.Searcher;
import org.lionsoul.ip2region.xdb.Version;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import org.springframework.scheduling.annotation.Scheduled;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * IP2Region离线库地理位置解析提供者
 * 支持 IPv4 和 IPv6 双协议并发安全查询
 * 优先从 classpath (resources/ip2region/) 加载数据库文件，
 * 若 classpath 不存在则回退到磁盘目录，最后尝试从网络下载
 * 
 * @author LeapYa
 */
@Slf4j
@Component
public class Ip2RegionProvider implements IpLocationProvider {

    // classpath 中的数据库文件路径
    private static final String IPV4_CLASSPATH = "ip2region/ip2region_v4.xdb";
    private static final String IPV6_CLASSPATH = "ip2region/ip2region_v6.xdb";

    // 数据库文件下载地址（仅当 classpath 和磁盘都没有时才使用）
    private static final String IPV4_DB_URL = "https://raw.githubusercontent.com/lionsoul2014/ip2region/master/data/ip2region_v4.xdb";
    private static final String IPV6_DB_URL = "https://raw.githubusercontent.com/lionsoul2014/ip2region/master/data/ip2region_v6.xdb";

    // 备用下载地址（国内镜像）
    private static final String IPV4_DB_URL_MIRROR = "https://gh-proxy.org/https://raw.githubusercontent.com/lionsoul2014/ip2region/master/data/ip2region_v4.xdb";
    private static final String IPV6_DB_URL_MIRROR = "https://gh-proxy.org/https://raw.githubusercontent.com/lionsoul2014/ip2region/master/data/ip2region_v6.xdb";

    // 数据存储目录（相对于用户目录）
    @Value("${ip2region.data-dir:#{null}}")
    private String customDataDir;

    // 自动更新开关：默认禁用（开发友好），生产环境通过环境变量 IP2REGION_UPDATE_ENABLED=true 开启
    @Value("${ip2region.update.enabled:false}")
    private boolean updateEnabled;

    private volatile Searcher ipv4Searcher;
    private volatile Searcher ipv6Searcher;
    private volatile boolean ipv4Available = false;
    private volatile boolean ipv6Available = false;

    // 旧 searcher 延迟关闭调度器（避免并发查询时 close 导致 NPE）
    private final ScheduledExecutorService searcherCloseScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ip2region-searcher-close");
                t.setDaemon(true);
                return t;
            });
    private Path dataDir;

    @PostConstruct
    public void initIp2Region() {
        // 初始化数据目录（用于更新持久化）
        initDataDir();

        // 初始化 IPv4 搜索器（只从 classpath 和磁盘加载，不阻塞下载）
        try {
            LongByteArray cBuff = loadDbContent(IPV4_CLASSPATH, "ip2region_v4.xdb");
            if (cBuff != null) {
                ipv4Searcher = Searcher.newWithBuffer(Version.IPv4, cBuff);
                ipv4Available = true;
                log.info("IP2Region IPv4 离线库初始化成功");
            }
        } catch (Exception e) {
            log.warn("IP2Region IPv4 离线库初始化失败: {}", e.getMessage());
            ipv4Searcher = null;
        }

        // 初始化 IPv6 搜索器
        try {
            LongByteArray cBuff = loadDbContent(IPV6_CLASSPATH, "ip2region_v6.xdb");
            if (cBuff != null) {
                ipv6Searcher = Searcher.newWithBuffer(Version.IPv6, cBuff);
                ipv6Available = true;
                log.info("IP2Region IPv6 离线库初始化成功");
            }
        } catch (Exception e) {
            log.debug("IP2Region IPv6 离线库初始化失败（可选）: {}", e.getMessage());
            ipv6Searcher = null;
        }

        if (ipv4Available || ipv6Available) {
            log.info("IP2Region 离线库初始化完成，IPv4支持: {}, IPv6支持: {}",
                    ipv4Available, ipv6Available);
        }
    }

    /**
     * 加载数据库内容：磁盘文件优先（自动更新持久化的版本）→ classpath（打包时的版本）
     * 磁盘文件只会在更新成功后才被写入，所以磁盘版本 >= classpath 版本
     */
    private LongByteArray loadDbContent(String classpathLocation, String fileName) {
        // 1. 优先从磁盘加载（自动更新持久化的最新版本）
        File diskFile = dataDir.resolve(fileName).toFile();
        if (diskFile.exists() && diskFile.length() > 1024 * 1024) {
            try {
                LongByteArray content = Searcher.loadContentFromFile(diskFile.getAbsolutePath());
                log.info("IP2Region 数据库从磁盘加载成功: {} ({}MB)", diskFile,
                        String.format("%.2f", content.length() / 1024.0 / 1024.0));
                return content;
            } catch (Exception e) {
                log.warn("从磁盘加载 {} 失败，回退到 classpath: {}", diskFile, e.getMessage());
            }
        }

        // 2. 回退到 classpath（打包时的版本）
        try {
            ClassPathResource resource = new ClassPathResource(classpathLocation);
            if (resource.exists()) {
                LongByteArray content;
                try (InputStream is = resource.getInputStream()) {
                    content = Searcher.loadContentFromInputStream(is);
                }
                if (content != null && content.length() > 1024 * 1024) {
                    log.info("IP2Region 数据库从 classpath 加载成功: {} ({}MB)", classpathLocation,
                            String.format("%.2f", content.length() / 1024.0 / 1024.0));
                    // 磁盘上没有该文件时，复制 classpath xdb 到磁盘
                    // 生产环境共享卷初始为空，Java 写入 xdb 后 Nginx 可通过共享卷读取做地区封禁
                    copyClasspathToDiskIfMissing(resource, fileName);
                    return content;
                }
            }
        } catch (Exception e) {
            log.debug("从 classpath 加载 {} 失败: {}", classpathLocation, e.getMessage());
        }

        return null;
    }

    /**
     * 磁盘上没有 xdb 文件时，将 classpath 的 xdb 复制到磁盘
     * 生产环境共享卷初始为空，Java 需要写入 xdb 供 Nginx 通过共享卷读取
     * 使用临时文件 + 原子 rename 避免写入中途被读到不完整文件
     */
    private void copyClasspathToDiskIfMissing(ClassPathResource resource, String fileName) {
        File destFile = dataDir.resolve(fileName).toFile();
        if (destFile.exists() && destFile.length() > 1024 * 1024) {
            return;  // 磁盘已有有效文件，无需复制
        }
        try {
            File tempFile = dataDir.resolve(fileName + ".copying").toFile();
            try (InputStream is = resource.getInputStream();
                    FileOutputStream fos = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }
            Files.move(tempFile.toPath(), destFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            log.info("IP2Region classpath xdb 已复制到磁盘（供 Nginx 共享卷读取）: {}", destFile);
        } catch (Exception e) {
            log.warn("复制 classpath xdb 到磁盘失败（不影响 Java 查询，但 Nginx 地区封禁可能无法工作）: {}", e.getMessage());
        }
    }

    /**
     * 初始化数据目录
     */
    private void initDataDir() {
        try {
            if (StringUtils.hasText(customDataDir)) {
                dataDir = Paths.get(customDataDir);
            } else {
                // 默认使用用户目录下的 .ip2region 文件夹
                String userHome = System.getProperty("user.home");
                dataDir = Paths.get(userHome, ".ip2region");
            }

            if (!Files.exists(dataDir)) {
                Files.createDirectories(dataDir);
                log.info("创建 IP2Region 数据目录: {}", dataDir);
            }
        } catch (Exception e) {
            log.warn("创建数据目录失败，使用临时目录: {}", e.getMessage());
            dataDir = Paths.get(System.getProperty("java.io.tmpdir"), "ip2region");
            try {
                Files.createDirectories(dataDir);
            } catch (Exception ex) {
                log.error("创建临时数据目录也失败: {}", ex.getMessage());
            }
        }
    }

    /**
     * 检查并更新数据库（公共入口）
     * 启动后异步触发 + 每月一号定时触发
     */
    public void updateDatabases() {
        if (!updateEnabled) {
            log.debug("IP2Region 自动更新已禁用（ip2region.update.enabled=false）");
            return;
        }
        log.info("开始检查 IP2Region 数据库更新...");
        tryUpdateDatabase(Version.IPv4, "ip2region_v4.xdb", IPV4_DB_URL, IPV4_DB_URL_MIRROR);
        tryUpdateDatabase(Version.IPv6, "ip2region_v6.xdb", IPV6_DB_URL, IPV6_DB_URL_MIRROR);
        log.info("IP2Region 数据库更新检查完成");
    }

    /**
     * 每月一号凌晨3点定时更新
     */
    @Scheduled(cron = "0 0 3 1 * ?")
    public void monthlyUpdate() {
        updateDatabases();
    }

    /**
     * 应用启动后60秒异步触发一次更新（不阻塞启动）
     */
    @Scheduled(initialDelayString = "${ip2region.update.startup-delay-ms:60000}", fixedDelay = Long.MAX_VALUE)
    public void startupUpdate() {
        updateDatabases();
    }

    /**
     * 尝试更新指定版本的 xdb 数据库文件
     * 流程：HEAD 请求拿 ETag → 对比本地 ETag → 不同则下载 → 验证 → 原子替换 → 切换 searcher
     * 任一步骤失败则回退，保留旧版本
     */
    private void tryUpdateDatabase(Version version, String fileName, String primaryUrl, String mirrorUrl) {
        // 1. HEAD 请求获取远端 ETag（镜像优先，国内直连 GitHub 基本超时）
        String remoteETag = fetchETag(mirrorUrl);
        if (remoteETag == null) {
            remoteETag = fetchETag(primaryUrl);
        }
        if (remoteETag == null) {
            log.warn("无法获取远端 ETag，跳过更新: {}", fileName);
            return;
        }

        // 2. 对比本地存储的 ETag
        File etagFile = dataDir.resolve(fileName + ".etag").toFile();
        String localETag = readLocalETag(etagFile);
        if (remoteETag.equals(localETag)) {
            log.debug("ETag 未变化，跳过更新: {} (etag={})", fileName, remoteETag);
            return;
        }

        // 3. ETag 不同，需要下载
        File tempFile = dataDir.resolve(fileName + ".downloading").toFile();
        try {
            if (!downloadTo(mirrorUrl, tempFile) && !downloadTo(primaryUrl, tempFile)) {
                log.warn("xdb 下载失败，保留旧版本: {}", fileName);
                return;
            }

            // 4. 验证临时文件
            Searcher newSearcher = validateAndLoadSearcher(version, tempFile);
            if (newSearcher == null) {
                log.warn("xdb 验证失败，保留旧版本: {}", fileName);
                deleteQuietly(tempFile);
                return;
            }

            // 5. 原子替换磁盘文件（Files.move REPLACE_EXISTING，不需要先 delete）
            File destFile = dataDir.resolve(fileName).toFile();
            try {
                Files.move(tempFile.toPath(), destFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception e) {
                log.warn("无法原子替换 xdb 文件，保留旧版本: {}, 错误: {}", fileName, e.getMessage());
                deleteQuietly(tempFile);
                closeQuietly(newSearcher);
                return;
            }

            // 6. 保存新 ETag + 切换内存 searcher（延迟关闭旧 searcher）
            writeLocalETag(etagFile, remoteETag);
            switchSearcher(version, newSearcher);
            log.info("xdb 更新成功: {} ({}MB, etag={})", fileName,
                    String.format("%.2f", destFile.length() / 1024.0 / 1024.0), remoteETag);
        } catch (Exception e) {
            log.warn("xdb 更新异常: {}, 错误: {}", fileName, e.getMessage());
            deleteQuietly(tempFile);
        }
    }

    /**
     * HEAD 请求获取远端 ETag
     */
    private String fetchETag(String urlStr) {
        HttpURLConnection connection = null;
        try {
            URL url = URI.create(urlStr).toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                log.debug("HEAD 请求失败: {} HTTP {}", urlStr, responseCode);
                return null;
            }

            String eTag = connection.getHeaderField("ETag");
            if (eTag == null || eTag.isEmpty()) {
                // 没有 ETag 时用 Content-Length 作为降级标识
                long contentLength = connection.getContentLengthLong();
                if (contentLength > 0) {
                    return "size:" + contentLength;
                }
                return null;
            }
            return eTag;
        } catch (Exception e) {
            log.debug("HEAD 请求异常: {}, 错误: {}", urlStr, e.getMessage());
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String readLocalETag(File etagFile) {
        try {
            if (etagFile.exists()) {
                return Files.readString(etagFile.toPath()).trim();
            }
        } catch (Exception e) {
            log.debug("读取本地 ETag 失败: {}", e.getMessage());
        }
        return null;
    }

    private void writeLocalETag(File etagFile, String eTag) {
        try {
            Files.writeString(etagFile.toPath(), eTag);
        } catch (Exception e) {
            log.debug("写入本地 ETag 失败: {}", e.getMessage());
        }
    }

    /**
     * 下载文件到指定路径（不做 rename，由调用方控制）
     */
    private boolean downloadTo(String urlStr, File destFile) {
        HttpURLConnection connection = null;
        try {
            URL url = URI.create(urlStr).toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(120000);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                log.warn("下载失败，HTTP状态码: {}, URL: {}", responseCode, urlStr);
                return false;
            }

            long contentLength = connection.getContentLengthLong();
            // Content-Length=-1 表示未知长度，不做大小检查，依赖实际下载字节兜底
            if (contentLength >= 0 && contentLength < 1024 * 1024) {
                log.warn("下载的文件太小 ({}bytes)，可能是错误响应", contentLength);
                return false;
            }

            long totalRead = 0;
            try (InputStream is = connection.getInputStream();
                    FileOutputStream fos = new FileOutputStream(destFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                    totalRead += bytesRead;
                }
            }
            // 实际下载大小兜底
            if (totalRead < 1024 * 1024) {
                log.warn("实际下载字节数过少 ({}bytes)，可能是错误响应", totalRead);
                deleteQuietly(destFile);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("下载文件失败: {}, 错误: {}", urlStr, e.getMessage());
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * 验证 xdb 文件：能加载 + 已知 IP 查询返回正常格式
     */
    private Searcher validateAndLoadSearcher(Version version, File dbFile) {
        try {
            LongByteArray cBuff = Searcher.loadContentFromFile(dbFile.getAbsolutePath());
            Searcher searcher = Searcher.newWithBuffer(version, cBuff);

            String[] testIps = version == Version.IPv4
                    ? new String[]{"8.8.8.8", "114.114.114.114"}
                    : new String[]{"2001:4860:4860::8888"};

            for (String ip : testIps) {
                String result = searcher.search(ip);
                if (result == null || !result.contains("|")) {
                    log.warn("xdb 验证失败: {} 返回异常结果: {}", ip, result);
                    return null;
                }
                String[] segs = result.split("\\|");
                if (segs.length < 4) {
                    log.warn("xdb 验证失败: {} 分段数不足: {}", ip, segs.length);
                    return null;
                }
            }
            return searcher;
        } catch (Exception e) {
            log.warn("xdb 加载验证失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 切换内存 searcher（volatile 写，延迟 60 秒关闭旧 searcher）
     * 延迟关闭是为了避免其他线程正在旧 searcher 上查询时被 close 导致异常
     */
    private void switchSearcher(Version version, Searcher newSearcher) {
        Searcher old;
        if (version == Version.IPv4) {
            old = ipv4Searcher;
            ipv4Searcher = newSearcher;
            ipv4Available = true;
        } else {
            old = ipv6Searcher;
            ipv6Searcher = newSearcher;
            ipv6Available = true;
        }
        // 延迟关闭旧 searcher，给在途查询留出完成时间
        if (old != null) {
            searcherCloseScheduler.schedule(() -> {
                try {
                    old.close();
                } catch (Exception e) {
                    log.debug("关闭旧 searcher 异常: {}", e.getMessage());
                }
            }, 60, TimeUnit.SECONDS);
        }
    }

    private void deleteQuietly(File file) {
        if (file != null && file.exists()) {
            try {
                file.delete();
            } catch (Exception ignored) {
            }
        }
    }

    private void closeQuietly(Searcher searcher) {
        if (searcher != null) {
            try {
                searcher.close();
            } catch (Exception ignored) {
            }
        }
    }

    @PreDestroy
    public void destroy() {
        searcherCloseScheduler.shutdownNow();
        if (ipv4Searcher != null) {
            try {
                ipv4Searcher.close();
            } catch (Exception e) {
                log.warn("关闭 IPv4 Searcher 时出错: {}", e.getMessage());
            }
        }
        if (ipv6Searcher != null) {
            try {
                ipv6Searcher.close();
            } catch (Exception e) {
                log.warn("关闭 IPv6 Searcher 时出错: {}", e.getMessage());
            }
        }
        log.info("IP2Region 服务已关闭");
    }

    @Override
    public ProviderType getProviderType() {
        return ProviderType.IP2_REGION;
    }

    /**
     * 解析IP地理位置，返回结构化数据 [nation, province, city]
     * <p>
     * IP2Region原始格式: 国家|区域|省份|城市|ISP
     * </p>
     *
     * @param ipAddress IP地址
     * @return 长度为3的数组 [nation, province, city]，解析失败时各字段为 null
     */
    public String[] resolveLocationDetail(String ipAddress) {
        String[] result = {null, null, null};
        if (!isAvailable()) return result;

        try {
            boolean isIPv6 = isIPv6Address(ipAddress);
            Searcher searcher = isIPv6 ? ipv6Searcher : ipv4Searcher;
            if (searcher == null) return result;

            String searchResult = searcher.search(ipAddress);
            if (!StringUtils.hasText(searchResult)) return result;

            String[] regions = searchResult.split("\\|");
            if (regions.length < 4) return result;

            String country = VisitRegionNormalizer.normalizeCountryName(regions[0]);
            if (!StringUtils.hasText(country)) {
                country = normalizeRegionValue(regions[0]);
            }
            String province = normalizeRegionValue(regions[1]);
            String city = normalizeRegionValue(regions[2]);

            // 国家
            if (StringUtils.hasText(country)) {
                result[0] = country;
            }

            // 省份/国家统计口径：国内保留省份，国外统一落到中文国家。
            if (StringUtils.hasText(country) && !VisitRegionNormalizer.isChina(country)) {
                result[1] = country;
            } else if (StringUtils.hasText(province)) {
                result[1] = province.replaceAll("省|市|自治区|特别行政区|壮族|回族|维吾尔", "");
            } else if (result[0] != null) {
                result[1] = result[0];
            }

            // 城市
            if (StringUtils.hasText(city)) {
                result[2] = city.replaceAll("市|地区|自治州|盟", "");
            }
        } catch (Exception e) {
            log.warn("IP2Region结构化解析IP失败: {}, 错误: {}", ipAddress, e.getMessage());
        }
        return result;
    }

    /**
     * 解析 IP 的 ISP 字段。
     * <p>
     * xdb v4 实际数据格式为 {@code 国家|区域|城市|运营商或公司名|国家代码}，
     * ISP 信息位于第 4 段（index 3）。常见取值示例：
     * <ul>
     *   <li>阿里云 IP 段：{@code 阿里}</li>
     *   <li>AWS IP 段：{@code Amazon.com, Inc.}</li>
     *   <li>Google DNS：{@code Google LLC}</li>
     *   <li>普通用户：{@code 联通} / {@code 电信} / {@code 移动}</li>
     *   <li>未识别：{@code 0}</li>
     * </ul>
     * <p>
     * 用于 IDC/机房 IP 识别。注意：xdb 的 ISP 段覆盖率并不完整，部分机房 IP
     * 可能标记为 "0"，识别为"弱信号"，不建议单独作为拦截依据。
     *
     * @param ipAddress IP 地址
     * @return ISP 名称；未识别或解析失败时返回 null
     */
    public String resolveIsp(String ipAddress) {
        if (!isAvailable()) return null;
        try {
            boolean isIPv6 = isIPv6Address(ipAddress);
            Searcher searcher = isIPv6 ? ipv6Searcher : ipv4Searcher;
            if (searcher == null) return null;

            String searchResult = searcher.search(ipAddress);
            if (!StringUtils.hasText(searchResult)) return null;

            String[] regions = searchResult.split("\\|");
            // xdb v4 格式：国家|区域|城市|运营商|国家代码
            if (regions.length < 4) return null;

            String isp = regions[3].trim();
            return "0".equals(isp) ? null : isp;
        } catch (Exception e) {
            log.warn("IP2Region解析ISP失败: {}, 错误: {}", ipAddress, e.getMessage());
            return null;
        }
    }

    private static final java.util.Set<String> DATACENTER_ISP_KEYWORDS = java.util.Set.of(
            "阿里", "腾讯", "华为", "amazon", "aws", "microsoft", "微软",
            "google", "谷歌", "oracle", "甲骨文", "digitalocean", "linode",
            "vultr", "contabo", "机房", "数据中心", "cloud"
    );

    /**
     * 判断是否为数据中心/云厂商 IP。
     * <p>
     * 仅在 ISP 段明确标注云厂商关键词时返回 true，基础运营商（联通/电信/移动）
     * 不在判定范围内，避免误伤手机用户。识别精度依赖 xdb 数据完整度，建议与行为分析
     * 或频率控制组合使用，不单独作为拦截依据。
     *
     * @param ipAddress IP 地址
     * @return 命中云厂商关键词返回 true；未识别或为普通用户返回 false
     */
    public boolean isDatacenterIp(String ipAddress) {
        String isp = resolveIsp(ipAddress);
        if (isp == null) return false;
        String lower = isp.toLowerCase(Locale.ROOT);
        for (String keyword : DATACENTER_ISP_KEYWORDS) {
            if (lower.contains(keyword)) return true;
        }
        return false;
    }

    @Override
    public String resolveLocation(String ipAddress) {
        if (!isAvailable()) {
            return "未知";
        }

        try {
            boolean isIPv6 = isIPv6Address(ipAddress);
            Searcher searcher = isIPv6 ? ipv6Searcher : ipv4Searcher;

            if (searcher == null) {
                log.debug("没有对应的 IP 搜索器可用，IP: {}, 是否IPv6: {}", ipAddress, isIPv6);
                return "未知";
            }

            String searchResult = searcher.search(ipAddress);
            if (StringUtils.hasText(searchResult)) {
                return parseResponse(searchResult);
            }
        } catch (Exception e) {
            log.warn("IP2Region离线库解析IP失败: {}, 错误: {}", ipAddress, e.getMessage());
        }

        return "未知";
    }

    @Override
    public boolean isAvailable() {
        return ipv4Available || ipv6Available;
    }

    @Override
    public boolean supportsIpType(String ipAddress) {
        if (!StringUtils.hasText(ipAddress)) {
            return false;
        }

        boolean isIPv6 = isIPv6Address(ipAddress);
        if (isIPv6) {
            return ipv6Available;
        } else {
            return ipv4Available;
        }
    }

    /**
     * 判断是否为IPv6地址
     */
    private boolean isIPv6Address(String ip) {
        if (!StringUtils.hasText(ip)) {
            return false;
        }
        return ip.contains(":") && !ip.contains(".");
    }

    private String normalizeRegionValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if ("0".equals(trimmed)
                || "未知".equals(trimmed)
                || "unknown".equalsIgnoreCase(trimmed)
                || "reserved".equalsIgnoreCase(trimmed)
                || "null".equalsIgnoreCase(trimmed)
                || "undefined".equalsIgnoreCase(trimmed)
                || "-".equals(trimmed)) {
            return null;
        }
        return trimmed;
    }

    /**
     * 解析IP2Region响应结果
     * IP2Region格式: 国家|区域|省份|城市|ISP
     * 
     * @param searchResult IP2Region搜索结果
     * @return 格式化的地理位置
     */
    private String parseResponse(String searchResult) {
        try {
            String[] regions = searchResult.split("\\|");
            if (regions.length >= 4) {
                String country = VisitRegionNormalizer.normalizeCountryName(regions[0]);
                if (!StringUtils.hasText(country)) {
                    country = normalizeRegionValue(regions[0]);
                }
                String province = normalizeRegionValue(regions[1]);

                // 如果不是中国，直接返回国家名
                if (StringUtils.hasText(country) && !"中国".equals(country)) {
                    return country;
                }

                // 中国地区处理
                if (StringUtils.hasText(province)) {
                    // 特殊地区处理
                    if ("香港".equals(province)) {
                        return "中国香港";
                    } else if ("澳门".equals(province)) {
                        return "中国澳门";
                    } else if ("台湾".equals(province)) {
                        return "中国台湾";
                    } else {
                        // 中国大陆省份，去掉后缀
                        return province.replaceAll("省|市|自治区|特别行政区", "");
                    }
                }

                if ("中国".equals(country)) {
                    return "中国";
                }
            }
        } catch (Exception e) {
            log.warn("解析IP2Region响应失败: {}", e.getMessage());
        }

        return "未知";
    }

    /**
     * 获取IP2Region服务状态
     */
    public boolean isSearcherInitialized() {
        return ipv4Searcher != null || ipv6Searcher != null;
    }

    /**
     * 获取IPv4支持状态
     */
    public boolean isIPv4Available() {
        return ipv4Available;
    }

    /**
     * 获取IPv6支持状态
     */
    public boolean isIPv6Available() {
        return ipv6Available;
    }

    /**
     * 获取数据目录路径
     */
    public Path getDataDir() {
        return dataDir;
    }

    /**
     * 遍历 xdb 提取所有不重复的 {国家代码 → xdb 国家名} 映射。
     * <p>
     * 用于 BanRegionNormalizer 在 Java 输入端校准地区封禁规则，把用户输入
     * （"美国"/"US"/"中华人民共和国" 等）统一映射为 xdb 实际返回的国家名
     * （如 "United States"/"中国"），与 Nginx 端 ban_check.lua 的精确匹配口径一致。
     * <p>
     * 实现方式：遍历 xdb vectorIndex 的所有 entry，收集不重复的 region 字符串，
     * 按 `|` 分割第 1 字段（国家名）和第 5 字段（国家代码）。
     * 启动时调用一次，结果缓存到 BanRegionNormalizer，不影响运行时性能。
     * <p>
     * 仅遍历 IPv4 xdb 即可覆盖所有国家（v4 和 v6 数据同源，国家名一致）。
     *
     * @return {国家代码 → xdb 国家名} 映射，例如 {"US" → "United States", "CN" → "中国"}；
     *         xdb 不可用时返回空 Map
     */
    public java.util.Map<String, String> extractCountryCodeToNameMap() {
        java.util.Map<String, String> result = new java.util.HashMap<>();
        java.util.Set<String> seenRegions = new java.util.HashSet<>();

        // 加载 xdb 内容（磁盘优先，回退 classpath）
        org.lionsoul.ip2region.xdb.LongByteArray cBuff = loadXdbContent("ip2region_v4.xdb", IPV4_CLASSPATH);
        if (cBuff == null) {
            log.warn("提取国家映射失败：IPv4 xdb 不可用，地区封禁规则校准将无法严格校验");
            return result;
        }

        // IPv4 segment index: start_ip(4) + end_ip(4) + data_len(2) + data_ptr(4) = 14 字节
        int segmentIndexSize = Version.IPv4.segmentIndexSize;  // 14
        int dBytes = Version.IPv4.bytes << 1;                   // 8（data_len 在 segBuff 中的偏移）
        byte[] segBuff = new byte[segmentIndexSize];

        // 遍历 vectorIndex（256*256 个 entry，每个 8 字节：sPtr + ePtr）
        java.util.Set<Long> visitedRanges = new java.util.HashSet<>();
        for (int il0 = 0; il0 < 256; il0++) {
            for (int il1 = 0; il1 < 256; il1++) {
                int idx = il0 * 256 * 8 + il1 * 8;
                long sPtr = cBuff.getUint32(Searcher.HeaderInfoLength + idx);
                long ePtr = cBuff.getUint32(Searcher.HeaderInfoLength + idx + 4);
                if (sPtr == 0 && ePtr == 0) continue;

                // 去重 range（vectorIndex 多个 entry 可能指向相同 segment 范围）
                long rangeKey = sPtr << 32 | ePtr;
                if (!visitedRanges.add(rangeKey)) continue;

                // 遍历 range 内的所有 segment
                int count = (int) ((ePtr - sPtr) / segmentIndexSize) + 1;
                for (int i = 0; i < count; i++) {
                    long p = sPtr + i * segmentIndexSize;
                    try {
                        cBuff.copy(p, segBuff, 0, segmentIndexSize);
                        int dataLen = org.lionsoul.ip2region.xdb.LittleEndian.getUint16(segBuff, dBytes);
                        long dataPtr = org.lionsoul.ip2region.xdb.LittleEndian.getUint32(segBuff, dBytes + 2);
                        if (dataLen == 0) continue;

                        byte[] regionBytes = cBuff.slice(dataPtr, dataLen);
                        String region = new String(regionBytes, java.nio.charset.StandardCharsets.UTF_8);
                        if (!seenRegions.add(region)) continue;

                        // xdb 格式：国家|区域|城市|ISP|国家代码
                        String[] parts = region.split("\\|");
                        if (parts.length >= 5) {
                            String countryName = parts[0].trim();
                            String countryCode = parts[4].trim();
                            if (!countryCode.isEmpty() && !"0".equals(countryCode)
                                    && !countryName.isEmpty() && !"0".equals(countryName)) {
                                result.putIfAbsent(countryCode, countryName);
                            }
                        }
                    } catch (Exception ignored) {
                        // 单条 segment 解析失败跳过，不影响整体
                    }
                }
            }
        }

        log.info("提取 xdb 国家映射完成：{} 个国家", result.size());
        return result;
    }

    /**
     * 加载 xdb 内容（磁盘优先 → classpath 回退），供 extractCountryCodeToNameMap 使用。
     * 不复用 ipv4Searcher 的 buffer，因为 Searcher 没有暴露 contentBuff getter，
     * 且这里只需启动时一次性读取，重新加载 22MB 的开销可接受。
     */
    private org.lionsoul.ip2region.xdb.LongByteArray loadXdbContent(String fileName, String classpath) {
        // 1. 磁盘优先
        File diskFile = dataDir.resolve(fileName).toFile();
        if (diskFile.exists() && diskFile.length() > 1024 * 1024) {
            try {
                return Searcher.loadContentFromFile(diskFile.getAbsolutePath());
            } catch (Exception e) {
                log.debug("从磁盘加载 {} 失败，回退 classpath: {}", fileName, e.getMessage());
            }
        }
        // 2. classpath 回退
        try {
            ClassPathResource resource = new ClassPathResource(classpath);
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    return Searcher.loadContentFromInputStream(is);
                }
            }
        } catch (Exception e) {
            log.debug("从 classpath 加载 {} 失败: {}", classpath, e.getMessage());
        }
        return null;
    }
}
