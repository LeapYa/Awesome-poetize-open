package com.ld.poetry.service.ai.tools.webfetch;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * SPA（单页应用）签名检测器。
 * <p>
 * 纯 CSR SPA（无 SSR/SSG）的 HTML 中只有空 {@code <div id="app">} + JS bundle 引用，
 * Readability4J 拿到这种 HTML 提取不出正文 — 因为正文根本不在 HTML 里。
 * 检测到 SPA 后触发 fallback 路径（JSON-LD articleBody / Jina Reader / 元数据）。
 */
public final class SpaDetector {

    private SpaDetector() {
    }

    /**
     * 综合判断 HTML 是否为纯 CSR SPA。
     * <p>
     * 判定规则：空容器 + (noscript 提示 OR script bundles ≥ 3) + body 文本 &lt; 500 字符
     *
     * @param document Jsoup 解析后的 DOM
     * @return true 表示检测到 SPA 签名
     */
    public static boolean isSpa(Document document) {
        if (document == null) {
            return false;
        }

        boolean hasEmptyMountPoint = hasEmptyMountPoint(document);
        boolean hasNoscriptHint = hasNoscriptHint(document);
        boolean hasManyScriptBundles = countScriptBundles(document) >= 3;

        int bodyTextLength = document.body() != null ? document.body().text().length() : 0;
        boolean bodyTextShort = bodyTextLength < 500;

        return hasEmptyMountPoint && (hasNoscriptHint || hasManyScriptBundles) && bodyTextShort;
    }

    private static boolean hasEmptyMountPoint(Document document) {
        // 常见 SPA 挂载点 id：Vue (#app/#__nuxt), React (#root/#__next)
        for (String id : new String[]{"app", "root", "__nuxt", "__next"}) {
            Element el = document.selectFirst("#" + id);
            if (el != null) {
                String text = el.text().trim();
                if (text.isEmpty() || text.length() < 100) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasNoscriptHint(Document document) {
        for (Element noscript : document.select("noscript")) {
            String text = noscript.text().toLowerCase();
            if (text.contains("javascript")
                    || text.contains("启用")
                    || text.contains("enable")
                    || text.contains("请启用")
                    || text.contains("please enable")) {
                return true;
            }
        }
        return false;
    }

    private static int countScriptBundles(Document document) {
        int count = 0;
        for (Element script : document.select("script[src]")) {
            String src = script.attr("src").toLowerCase();
            // 排除统计/广告脚本，只算应用 bundle
            if (src.contains("/assets/")
                    || src.contains("/static/")
                    || src.contains("/_nuxt/")
                    || src.contains("/_next/")
                    || src.contains("/dist/")) {
                count++;
            }
        }
        return count;
    }
}
