package com.ld.poetry.service.provider;

import lombok.extern.slf4j.Slf4j;
import org.lionsoul.ip2region.xdb.LongByteArray;
import org.lionsoul.ip2region.xdb.Searcher;
import org.lionsoul.ip2region.xdb.Version;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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

    private Searcher ipv4Searcher;
    private Searcher ipv6Searcher;
    private boolean ipv4Available = false;
    private boolean ipv6Available = false;
    private Path dataDir;

    @PostConstruct
    public void initIp2Region() {
        // 初始化数据目录（用于回退场景）
        initDataDir();

        // 初始化 IPv4 搜索器（优先 classpath → 磁盘 → 网络下载）
        try {
            LongByteArray cBuff = loadDbContent("ip2region_v4.xdb", IPV4_CLASSPATH, IPV4_DB_URL, IPV4_DB_URL_MIRROR);
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
            LongByteArray cBuff = loadDbContent("ip2region_v6.xdb", IPV6_CLASSPATH, IPV6_DB_URL, IPV6_DB_URL_MIRROR);
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
     * 加载数据库内容：优先 classpath → 磁盘文件 → 网络下载
     */
    private LongByteArray loadDbContent(String fileName, String classpathLocation, String primaryUrl, String mirrorUrl) {
        // 1. 优先从 classpath 加载（resources/ip2region/ 目录）
        try {
            ClassPathResource resource = new ClassPathResource(classpathLocation);
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    LongByteArray content = Searcher.loadContentFromInputStream(is);
                    if (content.length() > 1024 * 1024) {
                        log.info("IP2Region 数据库从 classpath 加载成功: {} ({}MB)", classpathLocation,
                                String.format("%.2f", content.length() / 1024.0 / 1024.0));
                        return content;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("从 classpath 加载 {} 失败: {}", classpathLocation, e.getMessage());
        }

        // 2. 回退到磁盘目录
        File diskFile = ensureDbFile(fileName, primaryUrl, mirrorUrl);
        if (diskFile != null && diskFile.exists()) {
            try {
                return Searcher.loadContentFromFile(diskFile.getAbsolutePath());
            } catch (Exception e) {
                log.warn("从磁盘加载 {} 失败: {}", diskFile, e.getMessage());
            }
        }

        return null;
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
     * 确保数据库文件存在，如果不存在则下载
     */
    private File ensureDbFile(String fileName, String primaryUrl, String mirrorUrl) {
        Path filePath = dataDir.resolve(fileName);
        File file = filePath.toFile();

        if (file.exists() && file.length() > 1024 * 1024) { // 文件存在且大于1MB
            log.debug("IP2Region 数据库文件已存在: {}", filePath);
            return file;
        }

        log.info("IP2Region 数据库文件不存在，开始下载: {}", fileName);

        // 尝试从主地址下载
        if (downloadFile(primaryUrl, file)) {
            log.info("IP2Region 数据库下载成功: {} ({}MB)", fileName,
                    String.format("%.2f", file.length() / 1024.0 / 1024.0));
            return file;
        }

        // 主地址失败，尝试镜像地址
        log.warn("从主地址下载失败，尝试镜像地址...");
        if (downloadFile(mirrorUrl, file)) {
            log.info("IP2Region 数据库从镜像下载成功: {} ({}MB)", fileName,
                    String.format("%.2f", file.length() / 1024.0 / 1024.0));
            return file;
        }

        log.error("IP2Region 数据库下载失败: {}", fileName);
        return null;
    }

    /**
     * 下载文件
     */
    private boolean downloadFile(String urlStr, File destFile) {
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

            // 检查文件大小
            long contentLength = connection.getContentLengthLong();
            if (contentLength < 1024 * 1024) { // 小于1MB说明可能是错误响应
                log.warn("下载的文件太小 ({}bytes)，可能是错误响应", contentLength);
                return false;
            }

            // 下载到临时文件
            File tempFile = new File(destFile.getParent(), destFile.getName() + ".tmp");
            try (InputStream is = connection.getInputStream();
                    FileOutputStream fos = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalRead = 0;
                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                    totalRead += bytesRead;
                    // 打印下载进度
                    if (totalRead % (5 * 1024 * 1024) == 0) {
                        log.info("下载进度: {}MB / {}MB",
                                totalRead / 1024 / 1024, contentLength / 1024 / 1024);
                    }
                }
            }

            // 下载完成，重命名
            if (destFile.exists()) {
                destFile.delete();
            }
            return tempFile.renameTo(destFile);

        } catch (Exception e) {
            log.warn("下载文件失败: {}, 错误: {}", urlStr, e.getMessage());
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @PreDestroy
    public void destroy() {
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

            String country = normalizeRegionValue(regions[0]);
            String province = normalizeRegionValue(regions[2]);
            String city = normalizeRegionValue(regions[3]);

            // 国家
            if (StringUtils.hasText(country)) {
                result[0] = country;
            }

            // 省份（如果没有具体省份，用国家名兜底，确保省份统计不为空）
            if (StringUtils.hasText(province)) {
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
                String country = normalizeRegionValue(regions[0]);
                String province = normalizeRegionValue(regions[2]);

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
}
