package com.ld.poetry.service.ai.tools;

import com.ld.poetry.entity.SysAiConfig;
import com.ld.poetry.service.SysAiConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 网页抓取回归测试（合并版）— 统一验证 gzip 解压修复、textarea CSS 反爬清洗、
 * bodyTextLength 噪音过滤逻辑在多类站点上的正确性。
 * <p>
 * 合并来源：
 * <ul>
 *   <li>原 {@code WebFetchToolsLeapyaTest} — gzip 解压回归（leapya 方法）</li>
 *   <li>原 {@code WebFetchBaiduDiagnosticTest} — textarea CSS 清洗回归（baidu 方法）</li>
 *   <li>多站点扩展 — juejin/runoob/vuejs/typescriptlang 覆盖不同站点类型</li>
 * </ul>
 * <p>
 * 改动背景：在计算 bodyTextLength 与 RAW_FALLBACK 输出前，统一移除
 * {@code script, style, noscript, template, textarea, iframe, svg} 噪音元素。
 * 该改动影响 Readability ratio 决策树（ratio = articleText / bodyTextLength），
 * 需覆盖多类站点验证不会误伤正常正文或漏过噪音。
 * <p>
 * 覆盖站点类型：
 * <ul>
 *   <li>SSG 博客（leapya）— 基线，Readability 应直接命中，验证 gzip 解压</li>
 *   <li>搜索引擎门户（baidu）— 含隐藏 textarea CSS 反爬手段，验证清洗</li>
 *   <li>中文技术社区 SSR（juejin）— 内容密集的 SSR 站点</li>
 *   <li>中文教程站 SSR（runoob）— 教程类内容密集站点</li>
 *   <li>文档站 SSG（vuejs.org）— VitePress 静态生成，含 SFC 代码示例</li>
 *   <li>文档站 SPA（typescriptlang）— 客户端渲染，本地路径可能降级到元数据</li>
 * </ul>
 * <p>
 * 隔离策略：禁用 Jina Reader fallback（{@code enableJinaReader=0}），
 * 强制走本地 Readability/Fetcher Chain 路径，独立验证清洗逻辑。
 * <p>
 * 运行：{@code mvn -pl poetry-web test -Dtest=WebFetchMultiSiteRegressionTest -Dwebfetch.network.test=true}
 *
 * @author LeapYa
 * @since 2026-06-29
 */
@EnabledIfSystemProperty(named = "webfetch.network.test", matches = "true")
class WebFetchMultiSiteRegressionTest {

    private WebFetchTools webFetchTools;

    @BeforeEach
    void setUp() {
        SysAiConfigService sysAiConfigService = Mockito.mock(SysAiConfigService.class);
        SysAiConfig config = new SysAiConfig();
        config.setEnableWebFetch(1);
        // 禁用 Jina，强制本地路径，独立验证清洗逻辑
        config.setEnableJinaReader(0);
        config.setJinaApiKey(null);
        Mockito.when(sysAiConfigService.getAiChatConfigInternal("default")).thenReturn(config);

        webFetchTools = new WebFetchTools(sysAiConfigService);
        webFetchTools.init();
    }

    // ========== 1. SSG 博客（基线，原 WebFetchToolsLeapyaTest 合并）==========

    @Test
    @DisplayName("leapya.com — SSG 博客，应走 READABILITY 返回可读中文正文（gzip 解压回归）")
    void leapyaSsgBlog() {
        String result = webFetchTools.fetchWebPage("https://www.leapya.com/", 0);
        assertCommonCleaning(result, "leapya");
        assertTrue(result.contains("Strategy: READABILITY"),
                "leapya 应走 READABILITY。header=" + headerOf(result));

        // 标题应成功提取（gzip 修复前 hasTitle=false / 标题提取失败）
        String title = extractField(headerOf(result), "Title:");
        assertTrue(title.length() > 0,
                "leapya 应成功提取标题。header=" + headerOf(result));

        // SSG 博客应有充足中文（gzip 乱码按 UTF-8 解码不会产生 CJK）
        long cjk = contentOf(result).chars()
                .filter(c -> Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS)
                .count();
        assertTrue(cjk > 30, "leapya 正文 CJK 应 > 30，实际 " + cjk);

        // 不应降级到 SPA 元数据兜底（若出现说明本地路径完全失败，仅剩元数据）
        assertFalse(contentOf(result).contains("该页面为客户端渲染 SPA"),
                "leapya 不应降级到 SPA 元数据兜底。content前200字=" + snippet(contentOf(result)));
    }

    // ========== 2. 搜索引擎门户（反爬 textarea CSS，原 WebFetchBaiduDiagnosticTest 合并）==========

    @Test
    @DisplayName("baidu.com — 门户反爬，应清洗 textarea CSS 且输出完整可见文本（非仅页脚）")
    void baiduPortalWithTextareaCss() {
        String result = webFetchTools.fetchWebPage("https://www.baidu.com", 0);
        assertCommonCleaning(result, "baidu");

        String header = headerOf(result);
        String content = contentOf(result);

        // baidu 正文完全不得含 <style 标签（textarea CSS 反爬清洗）
        // 修复前：RAW_FALLBACK 输出 257984 字符的 <style data-for="result"> CSS 噪音
        assertFalse(content.contains("<style"),
                "baidu 正文不得含 <style 标签（textarea CSS 反爬清洗）。content前200字=" + snippet(content));

        // 清洗后正文应短（修复前 257984）
        int totalLength = extractInt(header, "Total-Length:");
        assertTrue(totalLength < 5000,
                "baidu Total-Length 应 < 5000（修复前 257984），实际 " + totalLength);

        // 正文应包含百度品牌词或页脚链接文本（确认抓到真实页面而非空壳）
        boolean hasBrandSignal = content.contains("百度") || content.contains("Baidu")
                || content.contains("关于百度") || content.contains("使用百度");
        assertTrue(hasBrandSignal,
                "baidu 正文应包含品牌词或页脚链接。content前200字=" + snippet(content));

        // 低质量短页面检测触发后，RAW_FALLBACK 应输出比 Readability 更完整的可见文本，
        // 包括导航链接（新闻/hao123/地图/贴吧等），而非仅页脚备案信息。
        boolean hasNavSignal = content.contains("新闻") || content.contains("hao123")
                || content.contains("地图") || content.contains("贴吧")
                || content.contains("视频") || content.contains("图片");
        assertTrue(hasNavSignal,
                "baidu RAW_FALLBACK 应包含导航链接文字（新闻/hao123/地图等），而非仅页脚。content=" + snippet(content));

        // 清洗后正文远小于 32000 截断阈值，不应触发分页续读
        assertFalse(header.contains("Has-More: true"),
                "baidu 清洗后不应触发分页续读。header=" + header);
    }

    // ========== 3. 中文技术社区 SSR（大量正文）==========

    @Test
    @DisplayName("juejin.cn — 中文技术社区 SSR，应走 READABILITY 返回充足正文")
    void juejinChineseSsr() {
        String result = webFetchTools.fetchWebPage("https://juejin.cn", 0);
        assertCommonCleaning(result, "juejin");
        // 掘金首页为 SSR，应有充足正文
        int totalLength = extractInt(headerOf(result), "Total-Length:");
        assertTrue(totalLength > 500,
                "juejin Total-Length 应 > 500，实际 " + totalLength);
        // 应有标题
        String title = extractField(headerOf(result), "Title:");
        assertTrue(title.length() > 0, "juejin 应有标题。header=" + headerOf(result));
    }

    // ========== 4. 中文教程站 SSR（内容密集）==========

    @Test
    @DisplayName("runoob.com — 中文教程 SSR，应走 READABILITY 返回教程正文")
    void runoobTutorialSsr() {
        String result = webFetchTools.fetchWebPage(
                "https://www.runoob.com/js/js-tutorial.html", 0);
        assertCommonCleaning(result, "runoob");
        // 菜鸟教程为 SSR，正文丰富
        int totalLength = extractInt(headerOf(result), "Total-Length:");
        assertTrue(totalLength > 500,
                "runoob Total-Length 应 > 500，实际 " + totalLength);
        // 应有标题
        String title = extractField(headerOf(result), "Title:");
        assertTrue(title.length() > 0, "runoob 应有标题。header=" + headerOf(result));
    }

    // ========== 5. 文档站 SSG（VitePress）==========

    @Test
    @DisplayName("vuejs.org — VitePress SSG 文档，应提取出文档正文")
    void vuejsDocsSsg() {
        String result = webFetchTools.fetchWebPage(
                "https://vuejs.org/guide/introduction.html", 0);
        assertCommonCleaning(result, "vuejs");
        // VitePress 是 SSG，HTML 中应含预渲染正文，Readability 应能提取
        // 不强制 READABILITY（可能因导航/侧边栏比例触发 RAW_FALLBACK），但必须有正文
        // 注意：vuejs 文档含 Vue SFC 代码示例（<style scoped>...），属合法内容，不算 CSS 噪音
        int totalLength = extractInt(headerOf(result), "Total-Length:");
        assertTrue(totalLength > 500,
                "vuejs 文档 Total-Length 应 > 500，实际 " + totalLength);
    }

    // ========== 6. 文档站 SPA（客户端渲染）==========

    @Test
    @DisplayName("typescriptlang.org — SPA 文档，本地路径降级到元数据兜底（不得返回乱码）")
    void typescriptDocsSpa() {
        String result = webFetchTools.fetchWebPage(
                "https://www.typescriptlang.org/docs/handbook/2/everyday-types.html", 0);
        // SPA 站点本地路径可能降级到 METADATA_ONLY 或 RSS/Archive 兜底
        // 关键是不应返回乱码或 CSS/JS 噪音
        assertCommonCleaning(result, "typescriptlang");
        // 应有标题（即使降级到元数据也应提取到 title）
        String title = extractField(headerOf(result), "Title:");
        assertTrue(title.length() > 0,
                "typescriptlang 即使 SPA 降级也应有标题。header=" + headerOf(result));
    }

    // ========== 通用清洗断言（所有站点必须通过）==========

    /**
     * 所有站点必须通过的通用清洗断言：
     * <ol>
     *   <li>单个 {@code <style>...</style>} 块不得超过 2000 字符（CSS 噪音泄漏检测）。
     *       注意：Vue/React 文档的 SFC 代码示例会合法包含短小的 {@code <style scoped>} 块，
     *       不应误报。只有大段 CSS（如百度 250KB textarea CSS）才算噪音。</li>
     *   <li>单个 {@code <script>...</script>} 块不得超过 2000 字符（JS 噪音泄漏检测）。
     *       同理，代码示例中的 {@code <script setup>} 是合法内容。</li>
     *   <li>正文不得含 {@code <textarea} 标签（隐藏模板/CSS 反爬泄漏）</li>
     *   <li>U+FFFD 替换字符占比 &lt; 1%（二进制乱码检测）</li>
     *   <li>控制字符占比 &lt; 1%（二进制乱码检测）</li>
     *   <li>正文长度 &gt; 50（非空响应）</li>
     * </ol>
     */
    private void assertCommonCleaning(String result, String siteTag) {
        String header = headerOf(result);
        String content = contentOf(result);
        String diag = siteTag + " | header=" + header + " | content前200字=" + snippet(content);

        // 1. 单个 <style> 块不得超过 2000 字符
        //    百度 RAW_FALLBACK 的 CSS 噪音块为 257984 字符；vuejs SFC 代码示例仅 63 字符
        int maxStyleBlock = maxTagBlockLength(content, "<style", "</style>");
        assertTrue(maxStyleBlock < 2000,
                siteTag + " 单个 <style> 块长度应 < 2000（实际 " + maxStyleBlock
                        + "），可能泄漏 CSS 噪音。" + diag);

        // 2. 单个 <script> 块不得超过 2000 字符
        int maxScriptBlock = maxTagBlockLength(content, "<script", "</script>");
        assertTrue(maxScriptBlock < 2000,
                siteTag + " 单个 <script> 块长度应 < 2000（实际 " + maxScriptBlock
                        + "），可能泄漏 JS 噪音。" + diag);

        // 3. 不得含 <textarea 标签（隐藏 textarea 存放转义 CSS/模板是反爬手段，非正文）
        assertFalse(content.contains("<textarea"),
                siteTag + " 正文不得含 <textarea 标签。" + diag);

        // 4. U+FFFD 替换字符检测（gzip/编码乱码会大量产生）
        if (!content.isEmpty()) {
            long ffbd = content.chars().filter(c -> c == '\uFFFD').count();
            double ffbdRatio = (double) ffbd / content.length();
            assertTrue(ffbdRatio < 0.01,
                    siteTag + " U+FFFD 占比应 < 1%（实际 " + ffbd + "/" + content.length()
                            + "=" + String.format("%.4f", ffbdRatio) + "）。" + diag);
        }

        // 5. 控制字符检测（二进制乱码会含大量控制字节）
        if (!content.isEmpty()) {
            long control = content.chars()
                    .filter(c -> c < 0x20 && c != '\n' && c != '\r' && c != '\t')
                    .count();
            double controlRatio = (double) control / content.length();
            assertTrue(controlRatio < 0.01,
                    siteTag + " 控制字符占比应 < 1%（实际 " + control + "/" + content.length()
                            + "=" + String.format("%.4f", controlRatio) + "）。" + diag);
        }

        // 6. 正文长度下限（排除空响应/完全降级到错误页）
        assertTrue(content.length() > 50,
                siteTag + " 正文长度应 > 50，实际 " + content.length() + "。" + diag);
    }

    /**
     * 计算内容中所有 {@code <tag...>...</tag>} 块的最大长度（含标签本身）。
     * 用于检测是否泄漏大段 CSS/JS 噪音，同时允许代码示例中的短小标签块。
     *
     * @param openTag 开标签前缀（如 {@code "<style"}，匹配 {@code <style>}、{@code <style scoped>} 等）
     * @param closeTag 闭标签（如 {@code "</style>"})
     * @return 最大块长度；若无匹配返回 0
     */
    private int maxTagBlockLength(String content, String openTag, String closeTag) {
        int max = 0;
        int from = 0;
        while (true) {
            int start = content.indexOf(openTag, from);
            if (start < 0) break;
            int end = content.indexOf(closeTag, start);
            int blockLen = end >= 0 ? (end + closeTag.length() - start) : (content.length() - start);
            if (blockLen > max) max = blockLen;
            from = Math.max(start + openTag.length(), end + closeTag.length());
            if (end < 0) break;
        }
        return max;
    }

    // ===== 辅助方法 =====

    private String headerOf(String result) {
        int sep = result.indexOf("\n---\n");
        return sep >= 0 ? result.substring(0, sep) : result;
    }

    private String contentOf(String result) {
        int sep = result.indexOf("\n---\n");
        return sep >= 0 ? result.substring(sep + "\n---\n".length()) : "";
    }

    private String extractField(String header, String field) {
        for (String line : header.split("\n", 20)) {
            if (line.startsWith(field)) {
                return line.substring(field.length()).trim();
            }
        }
        return "";
    }

    private int extractInt(String header, String key) {
        int i = header.indexOf(key);
        if (i < 0) return -1;
        int start = i + key.length();
        int end = start;
        while (end < header.length() && Character.isWhitespace(header.charAt(end))) end++;
        int numStart = end;
        while (end < header.length() && Character.isDigit(header.charAt(end))) end++;
        try {
            return Integer.parseInt(header.substring(numStart, end));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String snippet(String s) {
        return s.substring(0, Math.min(200, s.length())).replace("\n", "\\n");
    }
}
