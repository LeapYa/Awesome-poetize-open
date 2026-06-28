package com.ld.poetry.service.ai.tools.webfetch;

import okhttp3.Dns;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * OkHttp 自定义 DNS 解析器 — 防 DNS 重绑定（DNS Rebinding）的核心防御。
 * <p>
 * 业务层 {@code InetAddress.getAllByName()} 校验后，OkHttp 内部连接池会再次解析 DNS。
 * 攻击者可在毫秒级时间差内切换解析记录（TTL=0）绕过业务层校验。
 * <p>
 * 本类将 IP 校验下沉到 OkHttp 连接池级别：当 OkHttp 准备建立 TCP Socket 时
 * 调用 {@link #lookup(String)}，在此处发起真实 DNS 查询并校验所有返回的 {@link InetAddress}，
 * 发现私有地址立即抛 {@link UnknownHostException} 阻断连接。解析与连接建立原子化连续发生。
 * <p>
 * 使用 {@link InetAddress} 二进制级别校验，不依赖字符串正则，
 * 防止十进制 {@code 2130706433}、八进制 {@code 0177.0.0.0x1}、IPv4-mapped IPv6 {@code ::ffff:127.0.0.1} 等编码格式逃逸。
 */
public class SafeDns implements Dns {

    private static final Logger log = LoggerFactory.getLogger(SafeDns.class);

    @Override
    public List<InetAddress> lookup(String hostname) throws UnknownHostException {
        List<InetAddress> addresses = Dns.SYSTEM.lookup(hostname);
        for (InetAddress addr : addresses) {
            if (isPrivateOrReserved(addr)) {
                log.warn("SafeDns 拦截到内网/保留地址: host={}, addr={}", hostname, addr.getHostAddress());
                throw new UnknownHostException(
                        "Blocked by SSRF protection: " + hostname + " -> " + addr.getHostAddress());
            }
        }
        return addresses;
    }

    /**
     * 判断 {@link InetAddress} 是否为内网/保留地址。
     * <p>
     * 优先使用 Java 内置方法（覆盖 IPv4 10/8、172.16/12、192.168/16 + IPv6 部分场景），
     * 显式补充 isSiteLocalAddress 未覆盖的网段：IPv6 ULA {@code fc00::/7}、
     * IPv4 {@code 0.0.0.0/8}（本网段）、{@code 100.64.0.0/10}（CGNAT）、{@code 240.0.0.0/4}（保留）。
     */
    public static boolean isPrivateOrReserved(InetAddress addr) {
        // Java 内置方法覆盖 IPv4 私有网段 + 部分 IPv6 场景
        if (addr.isLoopbackAddress()
                || addr.isAnyLocalAddress()
                || addr.isLinkLocalAddress()
                || addr.isSiteLocalAddress()
                || addr.isMulticastAddress()) {
            return true;
        }

        byte[] bytes = addr.getAddress();

        // IPv6 处理
        if (bytes.length == 16) {
            // IPv6 ULA fc00::/7（isSiteLocalAddress 不覆盖此网段）
            // fc00::/7 意味着首字节高 7 位为 1111110，即 (byte & 0xFE) == 0xFC
            // 覆盖 fc00::/8 (fc..) 和 fd00::/8 (fd..)
            if ((bytes[0] & 0xFE) == (byte) 0xFC) {
                return true;
            }
            // IPv4-mapped IPv6 ::ffff:a.b.c.d（攻击者用以绕过纯 IPv4 黑名单）
            // 检查 ::ffff: 前缀：前 10 字节为 0，第 11、12 字节为 0xff
            boolean isV4Mapped = true;
            for (int i = 0; i < 10; i++) {
                if (bytes[i] != 0) {
                    isV4Mapped = false;
                    break;
                }
            }
            if (isV4Mapped && bytes[10] == (byte) 0xFF && bytes[11] == (byte) 0xFF) {
                // 取后 4 字节作为 IPv4 递归判断（Java isSiteLocalAddress + 额外保留网段）
                return isAdditionalPrivateV4(bytes[12], bytes[13], bytes[14], bytes[15])
                        || isJavaBuiltinPrivateV4(bytes[12], bytes[13], bytes[14], bytes[15]);
            }
            return false;
        }

        // IPv4 处理（Java 内置方法已在上方通过，这里只查额外保留网段）
        if (bytes.length == 4) {
            return isAdditionalPrivateV4(bytes[0], bytes[1], bytes[2], bytes[3]);
        }
        return false;
    }

    /**
     * 显式覆盖 Java {@code isSiteLocalAddress} 未覆盖的 IPv4 保留网段。
     * <p>
     * 已知 {@code isSiteLocalAddress} 在 OpenJDK 8+ 覆盖 10/8、172.16/12、192.168/16，
     * 但不覆盖：
     * <ul>
     *   <li>{@code 0.0.0.0/8}（"本网"段，含 0.0.0.0）</li>
     *   <li>{@code 100.64.0.0/10}（CGNAT 共享地址段，运营商级 NAT）</li>
     *   <li>{@code 240.0.0.0/4}（保留作未来使用，含 255.255.255.255 广播）</li>
     * </ul>
     */
    private static boolean isAdditionalPrivateV4(byte b0, byte b1, byte b2, byte b3) {
        int ip0 = b0 & 0xFF;
        int ip1 = b1 & 0xFF;
        // 0.0.0.0/8（"本网"段，含 0.0.0.0）
        if (ip0 == 0) {
            return true;
        }
        // 100.64.0.0/10（CGNAT 共享地址段，运营商级 NAT）
        if (ip0 == 100 && (ip1 & 0xC0) == 64) {
            return true;
        }
        // 240.0.0.0/4（保留作未来使用，含 255.255.255.255 广播）
        if ((ip0 & 0xF0) == 0xF0) {
            return true;
        }
        return false;
    }

    /**
     * 对 IPv4-mapped IPv6 调用 Java 内置方法（覆盖 10/8、172.16/12、192.168/16、loopback、link-local、multicast）。
     * 单独抽出以便 IPv4-mapped IPv6 路径复用，避免重复创建 InetAddress 对象。
     */
    private static boolean isJavaBuiltinPrivateV4(byte b0, byte b1, byte b2, byte b3) {
        try {
            InetAddress v4 = InetAddress.getByAddress(new byte[]{b0, b1, b2, b3});
            return v4.isLoopbackAddress()
                    || v4.isSiteLocalAddress()
                    || v4.isLinkLocalAddress()
                    || v4.isMulticastAddress()
                    || v4.isAnyLocalAddress();
        } catch (UnknownHostException ignored) {
            // 不会发生，4 字节始终合法
            return false;
        }
    }
}
