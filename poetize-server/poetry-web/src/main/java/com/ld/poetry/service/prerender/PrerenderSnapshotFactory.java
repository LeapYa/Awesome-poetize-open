package com.ld.poetry.service.prerender;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.ld.poetry.dao.ArticleMapper;
import com.ld.poetry.dao.ArticleTranslationMapper;
import com.ld.poetry.entity.Article;
import com.ld.poetry.entity.ArticleTranslation;
import com.ld.poetry.entity.Sort;
import com.ld.poetry.utils.CommonQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
class PrerenderSnapshotFactory {

    private final CommonQuery commonQuery;
    private final ArticleMapper articleMapper;
    private final ArticleTranslationMapper articleTranslationMapper;
    private final PrerenderLanguageSupport languageSupport;

    PrerenderSnapshot createSnapshot(PrerenderRequest request) {
        String sourceLanguage = languageSupport.getSourceLanguage();
        if (!needsFullGraph(request.seeds())) {
            List<Integer> articleIds = request.seeds().stream()
                    .filter(ArticleNode.class::isInstance)
                    .map(ArticleNode.class::cast)
                    .map(ArticleNode::articleId)
                    .distinct()
                    .toList();
            return new PrerenderSnapshot(sourceLanguage, List.of(), List.of(), loadArticleLanguages(articleIds, sourceLanguage));
        }

        List<Sort> sorts = new ArrayList<>(commonQuery.getSortInfo());
        sorts.sort(Comparator.comparing((Sort sort) -> sort.getSortType() == null ? 0 : sort.getSortType())
                .thenComparing(sort -> sort.getPriority() == null ? 0 : sort.getPriority())
                .thenComparing(sort -> sort.getSortName() == null ? "" : sort.getSortName(), String.CASE_INSENSITIVE_ORDER));

        List<Article> articles = new LambdaQueryChainWrapper<>(articleMapper)
                .select(Article::getId)
                .eq(Article::getViewStatus, Boolean.TRUE)
                .eq(Article::getDeleted, Boolean.FALSE)
                .orderByDesc(Article::getCreateTime)
                .list();

        List<Integer> articleIds = articles.stream().map(Article::getId).toList();
        return new PrerenderSnapshot(sourceLanguage, sorts, articleIds, loadArticleLanguages(articleIds, sourceLanguage));
    }

    private boolean needsFullGraph(Set<PrerenderNode> seeds) {
        return seeds.stream().anyMatch(node -> node instanceof SiteRootNode
                || node instanceof StaticCatalogNode
                || node instanceof SortCatalogNode
                || node instanceof ArticleCatalogNode);
    }

    private Map<Integer, List<String>> loadArticleLanguages(List<Integer> articleIds, String sourceLanguage) {
        if (CollectionUtils.isEmpty(articleIds)) {
            return Map.of();
        }

        LinkedHashMap<Integer, LinkedHashSet<String>> languageMap = new LinkedHashMap<>();
        for (Integer articleId : articleIds) {
            if (articleId != null) {
                LinkedHashSet<String> languages = new LinkedHashSet<>();
                languages.add(sourceLanguage);
                languageMap.put(articleId, languages);
            }
        }

        List<ArticleTranslation> translations = new LambdaQueryChainWrapper<>(articleTranslationMapper)
                .select(ArticleTranslation::getArticleId, ArticleTranslation::getLanguage)
                .in(ArticleTranslation::getArticleId, articleIds)
                .orderByAsc(ArticleTranslation::getArticleId)
                .orderByAsc(ArticleTranslation::getCreateTime)
                .list();

        for (ArticleTranslation translation : translations) {
            if (translation == null || translation.getArticleId() == null) {
                continue;
            }
            if (!languageSupport.isSupportedLanguage(translation.getLanguage())) {
                continue;
            }
            languageMap.computeIfAbsent(translation.getArticleId(), key -> new LinkedHashSet<>()).add(translation.getLanguage().trim());
        }

        LinkedHashMap<Integer, List<String>> resolved = new LinkedHashMap<>();
        for (Map.Entry<Integer, LinkedHashSet<String>> entry : languageMap.entrySet()) {
            resolved.put(entry.getKey(), List.copyOf(new ArrayList<>(entry.getValue())));
        }
        return resolved;
    }
}
