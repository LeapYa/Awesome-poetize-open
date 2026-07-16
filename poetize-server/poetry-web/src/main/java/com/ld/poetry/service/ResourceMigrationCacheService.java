package com.ld.poetry.service;

import com.ld.poetry.constants.CacheConstants;
import com.ld.poetry.service.prerender.PrerenderFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceMigrationCacheService {

    private final CacheService cacheService;
    private final PrerenderFacade prerenderFacade;

    public void invalidateAfterMigration() {
        try {
            cacheService.deleteKeysByPattern(CacheConstants.ARTICLE_CACHE_PREFIX + "*");
            cacheService.deleteKeysByPattern(CacheConstants.ARTICLE_LIST_PREFIX + "*");
            cacheService.deleteKeysByPattern(CacheConstants.USER_ARTICLE_LIST_PREFIX + "*");
            cacheService.deleteKeysByPattern(CacheConstants.SEARCH_ARTICLE_PREFIX + "*");
            cacheService.deleteKeysByPattern(CacheConstants.USER_CACHE_PREFIX + "*");
            cacheService.deleteKeysByPattern(CacheConstants.COMMENT_LIST_PREFIX + "*");
            cacheService.deleteKeysByPattern(CacheConstants.COMMENT_COUNT_PREFIX + "*");
            cacheService.deleteKeysByPattern(CacheConstants.SYS_CONFIG_PREFIX + "*");
            cacheService.deleteKeysByPattern(CacheConstants.CACHE_PREFIX + "seo:*");
            cacheService.deleteKey(CacheConstants.HOT_ARTICLES_KEY);
            cacheService.deleteKey(CacheConstants.FAMILY_LIST_KEY);
            cacheService.evictSortArticleList();
            cacheService.evictWebInfo();
        } catch (Exception e) {
            log.warn("资源迁移后清理缓存失败，将由缓存过期机制兜底", e);
        }

        try {
            prerenderFacade.rebuildSiteAsync(Duration.ofSeconds(1));
        } catch (Exception e) {
            log.warn("资源迁移后触发预渲染失败", e);
        }
    }
}