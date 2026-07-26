package com.ld.poetry.service;

/**
 * RSS订阅源服务接口
 *
 * @author LeapYa
 * @since 2026-07-25
 */
public interface RssService {

    /**
     * 生成RSS 2.0订阅源内容（优先使用缓存）
     *
     * @return rss.xml内容
     */
    String generateRss();

    /**
     * 生成指定语言的RSS 2.0订阅源内容（优先使用缓存）
     *
     * @param language 目标语言代码（null/空表示源语言基础版）
     * @return rss.xml内容
     */
    String generateRss(String language);

    /**
     * 生成RSS 2.0订阅源内容（不使用缓存）
     *
     * @return rss.xml内容
     */
    String generateRssDirect();

    /**
     * 生成指定语言的RSS 2.0订阅源内容（不使用缓存）
     *
     * @param language 目标语言代码（null/空表示源语言基础版）
     * @return rss.xml内容
     */
    String generateRssDirect(String language);

    /**
     * 清除RSS缓存
     */
    void clearRssCache();
}
