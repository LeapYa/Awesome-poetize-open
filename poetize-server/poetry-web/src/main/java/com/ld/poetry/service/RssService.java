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
     * 生成RSS 2.0订阅源内容（不使用缓存）
     *
     * @return rss.xml内容
     */
    String generateRssDirect();

    /**
     * 清除RSS缓存
     */
    void clearRssCache();
}
