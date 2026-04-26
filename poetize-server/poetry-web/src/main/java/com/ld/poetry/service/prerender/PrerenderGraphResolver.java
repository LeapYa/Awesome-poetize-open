package com.ld.poetry.service.prerender;

import com.ld.poetry.entity.Label;
import com.ld.poetry.entity.Sort;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

@Component
class PrerenderGraphResolver {

    List<PrerenderNode> childrenOf(PrerenderNode node, PrerenderSnapshot snapshot) {
        if (node instanceof SiteRootNode) {
            return List.of(new StaticCatalogNode(), new SortCatalogNode(), new ArticleCatalogNode());
        }
        if (node instanceof StaticCatalogNode) {
            return List.of(
                    new StaticPageNode(PrerenderStaticPage.HOME),
                    new StaticPageNode(PrerenderStaticPage.FRIENDS),
                    new StaticPageNode(PrerenderStaticPage.MUSIC),
                    new StaticPageNode(PrerenderStaticPage.FAVORITES),
                    new StaticPageNode(PrerenderStaticPage.ABOUT),
                    new StaticPageNode(PrerenderStaticPage.MESSAGE),
                    new StaticPageNode(PrerenderStaticPage.WEIYAN),
                    new StaticPageNode(PrerenderStaticPage.LOVE),
                    new StaticPageNode(PrerenderStaticPage.TRAVEL),
                    new StaticPageNode(PrerenderStaticPage.PRIVACY),
                    new StaticPageNode(PrerenderStaticPage.LETTER));
        }
        if (node instanceof SortCatalogNode) {
            List<PrerenderNode> children = new ArrayList<>();
            children.add(new SortIndexNode());
            for (Sort sort : snapshot.sorts()) {
                if (sort == null || sort.getId() == null) {
                    continue;
                }
                children.add(new SortNode(sort.getId()));
                if (CollectionUtils.isEmpty(sort.getLabels())) {
                    continue;
                }
                for (Label label : sort.getLabels()) {
                    if (label != null && label.getId() != null) {
                        children.add(new SortLabelNode(sort.getId(), label.getId()));
                    }
                }
            }
            return children;
        }
        if (node instanceof ArticleCatalogNode) {
            return snapshot.publicArticleIds().stream()
                    .map(ArticleNode::new)
                    .map(PrerenderNode.class::cast)
                    .toList();
        }
        if (node instanceof ArticleNode articleNode) {
            return snapshot.languagesForArticle(articleNode.articleId()).stream()
                    .map(language -> new ArticleLanguageNode(articleNode.articleId(), language))
                    .map(PrerenderNode.class::cast)
                    .toList();
        }
        return List.of();
    }
}
