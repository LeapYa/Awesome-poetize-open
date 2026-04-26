package com.ld.poetry.service.prerender;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

interface PrerenderNodeRenderer {

    boolean supports(PrerenderNode node);

    void render(PrerenderNode node, PrerenderSnapshot snapshot);
}

@Component
@RequiredArgsConstructor
class PrerenderNodeRendererRegistry {

    private final List<PrerenderNodeRenderer> renderers;

    void render(PrerenderNode node, PrerenderSnapshot snapshot) {
        for (PrerenderNodeRenderer renderer : renderers) {
            if (renderer.supports(node)) {
                renderer.render(node, snapshot);
                return;
            }
        }
        throw new IllegalStateException("未找到匹配的预渲染渲染器: " + node.key());
    }
}

@Component
@RequiredArgsConstructor
class StaticPageNodeRenderer implements PrerenderNodeRenderer {

    private final PrerenderService prerenderService;

    @Override
    public boolean supports(PrerenderNode node) {
        return node instanceof StaticPageNode;
    }

    @Override
    public void render(PrerenderNode node, PrerenderSnapshot snapshot) {
        StaticPageNode staticPageNode = (StaticPageNode) node;
        switch (staticPageNode.page()) {
            case HOME -> prerenderService.renderHomePage();
            case FRIENDS -> prerenderService.renderFriendsPage();
            case MUSIC -> prerenderService.renderMusicPage();
            case FAVORITES -> prerenderService.renderFavoritesPage();
            case ABOUT -> prerenderService.renderAboutPage();
            case MESSAGE -> prerenderService.renderMessagePage();
            case WEIYAN -> prerenderService.renderWeiYanPage();
            case LOVE -> prerenderService.renderLovePage();
            case TRAVEL -> prerenderService.renderTravelPage();
            case PRIVACY -> prerenderService.renderPrivacyPage();
            case LETTER -> prerenderService.renderLetterPage();
        }
    }
}

@Component
@RequiredArgsConstructor
class SortIndexNodeRenderer implements PrerenderNodeRenderer {

    private final PrerenderService prerenderService;

    @Override
    public boolean supports(PrerenderNode node) {
        return node instanceof SortIndexNode;
    }

    @Override
    public void render(PrerenderNode node, PrerenderSnapshot snapshot) {
        prerenderService.renderSortIndexPage();
    }
}

@Component
@RequiredArgsConstructor
class SortNodeRenderer implements PrerenderNodeRenderer {

    private final PrerenderService prerenderService;

    @Override
    public boolean supports(PrerenderNode node) {
        return node instanceof SortNode;
    }

    @Override
    public void render(PrerenderNode node, PrerenderSnapshot snapshot) {
        SortNode sortNode = (SortNode) node;
        prerenderService.renderCategoryPage(sortNode.sortId(), null);
    }
}

@Component
@RequiredArgsConstructor
class SortLabelNodeRenderer implements PrerenderNodeRenderer {

    private final PrerenderService prerenderService;

    @Override
    public boolean supports(PrerenderNode node) {
        return node instanceof SortLabelNode;
    }

    @Override
    public void render(PrerenderNode node, PrerenderSnapshot snapshot) {
        SortLabelNode sortLabelNode = (SortLabelNode) node;
        prerenderService.renderCategoryPage(sortLabelNode.sortId(), sortLabelNode.labelId());
    }
}

@Component
@RequiredArgsConstructor
class ArticleLanguageNodeRenderer implements PrerenderNodeRenderer {

    private final PrerenderService prerenderService;

    @Override
    public boolean supports(PrerenderNode node) {
        return node instanceof ArticleLanguageNode;
    }

    @Override
    public void render(PrerenderNode node, PrerenderSnapshot snapshot) {
        ArticleLanguageNode articleLanguageNode = (ArticleLanguageNode) node;
        prerenderService.renderArticle(articleLanguageNode.articleId(), List.of(articleLanguageNode.language()));
    }
}
