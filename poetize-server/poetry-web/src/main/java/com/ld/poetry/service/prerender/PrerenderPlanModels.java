package com.ld.poetry.service.prerender;

import com.ld.poetry.entity.Sort;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

record PrerenderRequest(String description, Set<PrerenderNode> seeds, List<PrerenderCleanup> cleanups,
                        boolean clearTemplateCache) {

    PrerenderRequest {
        seeds = Collections.unmodifiableSet(new LinkedHashSet<>(seeds == null ? Set.of() : seeds));
        cleanups = Collections.unmodifiableList(new ArrayList<>(cleanups == null ? List.of() : cleanups));
        description = description == null ? "未命名预渲染请求" : description;
    }

    boolean isNoop() {
        return seeds.isEmpty() && cleanups.isEmpty() && !clearTemplateCache;
    }
}

enum PrerenderCleanupType {
    ARTICLE,
    CATEGORY_PAGE,
    PAGE,
    SORT_INDEX
}

record PrerenderCleanup(PrerenderCleanupType type, String pageType, Integer sortId, Integer labelId,
                        Integer articleId, String articleSlug) {

    static PrerenderCleanup article(Integer articleId) {
        return article(articleId, null);
    }

    static PrerenderCleanup article(Integer articleId, String articleSlug) {
        return new PrerenderCleanup(PrerenderCleanupType.ARTICLE, null, null, null, articleId, articleSlug);
    }

    static PrerenderCleanup category(Integer sortId, Integer labelId) {
        return new PrerenderCleanup(PrerenderCleanupType.CATEGORY_PAGE, null, sortId, labelId, null, null);
    }

    static PrerenderCleanup page(String pageType) {
        return new PrerenderCleanup(PrerenderCleanupType.PAGE, pageType, null, null, null, null);
    }

    static PrerenderCleanup sortIndex() {
        return new PrerenderCleanup(PrerenderCleanupType.SORT_INDEX, null, null, null, null, null);
    }
}

record PrerenderPlan(String description, List<PrerenderNode> renderNodes) {
    PrerenderPlan {
        renderNodes = Collections.unmodifiableList(new ArrayList<>(renderNodes == null ? List.of() : renderNodes));
        description = description == null ? "未命名预渲染计划" : description;
    }
}

record PrerenderSnapshot(String sourceLanguage, List<Sort> sorts, List<Integer> publicArticleIds,
                         Map<Integer, List<String>> articleLanguagesById) {

    PrerenderSnapshot {
        sourceLanguage = sourceLanguage == null || sourceLanguage.isBlank() ? "zh" : sourceLanguage;
        sorts = Collections.unmodifiableList(new ArrayList<>(sorts == null ? List.of() : sorts));
        publicArticleIds = Collections.unmodifiableList(new ArrayList<>(publicArticleIds == null ? List.of() : publicArticleIds));
        Map<Integer, List<String>> normalizedLanguages = new LinkedHashMap<>();
        if (articleLanguagesById != null) {
            for (Map.Entry<Integer, List<String>> entry : articleLanguagesById.entrySet()) {
                normalizedLanguages.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
            }
        }
        articleLanguagesById = Collections.unmodifiableMap(normalizedLanguages);
    }

    List<String> languagesForArticle(Integer articleId) {
        return articleLanguagesById.getOrDefault(articleId, List.of(sourceLanguage));
    }
}
