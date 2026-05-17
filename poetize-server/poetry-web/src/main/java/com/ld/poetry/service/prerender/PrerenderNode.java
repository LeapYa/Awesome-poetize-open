package com.ld.poetry.service.prerender;

import java.util.Objects;

enum PrerenderStaticPage {
    HOME("home"),
    FRIENDS("friends"),
    MUSIC("music"),
    FAVORITES("favorites"),
    ABOUT("about"),
    MESSAGE("message"),
    WEIYAN("weiYan"),
    LOVE("love"),
    TRAVEL("travel"),
    PRIVACY("privacy"),
    LETTER("letter");

    private final String route;

    PrerenderStaticPage(String route) {
        this.route = route;
    }

    String route() {
        return route;
    }
}

sealed interface PrerenderNode permits SiteRootNode, AdminShellNode, StaticCatalogNode, SortCatalogNode,
        ArticleCatalogNode, StaticPageNode, SortIndexNode, SortNode, SortLabelNode,
        ArticleNode, ArticleLanguageNode {

    String key();

    int priority();

    default boolean renderable() {
        return true;
    }
}

record SiteRootNode() implements PrerenderNode {
    @Override
    public String key() {
        return "site-root";
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public boolean renderable() {
        return false;
    }
}

record AdminShellNode() implements PrerenderNode {
    @Override
    public String key() {
        return "admin:shell";
    }

    @Override
    public int priority() {
        return 8;
    }
}

record StaticCatalogNode() implements PrerenderNode {
    @Override
    public String key() {
        return "catalog:static";
    }

    @Override
    public int priority() {
        return 5;
    }

    @Override
    public boolean renderable() {
        return false;
    }
}

record SortCatalogNode() implements PrerenderNode {
    @Override
    public String key() {
        return "catalog:sort";
    }

    @Override
    public int priority() {
        return 15;
    }

    @Override
    public boolean renderable() {
        return false;
    }
}

record ArticleCatalogNode() implements PrerenderNode {
    @Override
    public String key() {
        return "catalog:article";
    }

    @Override
    public int priority() {
        return 45;
    }

    @Override
    public boolean renderable() {
        return false;
    }
}

record StaticPageNode(PrerenderStaticPage page) implements PrerenderNode {
    StaticPageNode {
        Objects.requireNonNull(page, "page");
    }

    @Override
    public String key() {
        return "static:" + page.route();
    }

    @Override
    public int priority() {
        return 10;
    }
}

record SortIndexNode() implements PrerenderNode {
    @Override
    public String key() {
        return "sort:index";
    }

    @Override
    public int priority() {
        return 20;
    }
}

record SortNode(Integer sortId) implements PrerenderNode {
    SortNode {
        Objects.requireNonNull(sortId, "sortId");
    }

    @Override
    public String key() {
        return "sort:" + sortId;
    }

    @Override
    public int priority() {
        return 30;
    }
}

record SortLabelNode(Integer sortId, Integer labelId) implements PrerenderNode {
    SortLabelNode {
        Objects.requireNonNull(sortId, "sortId");
        Objects.requireNonNull(labelId, "labelId");
    }

    @Override
    public String key() {
        return "sort:" + sortId + ":label:" + labelId;
    }

    @Override
    public int priority() {
        return 40;
    }
}

record ArticleNode(Integer articleId) implements PrerenderNode {
    ArticleNode {
        Objects.requireNonNull(articleId, "articleId");
    }

    @Override
    public String key() {
        return "article:" + articleId;
    }

    @Override
    public int priority() {
        return 50;
    }

    @Override
    public boolean renderable() {
        return false;
    }
}

record ArticleLanguageNode(Integer articleId, String language) implements PrerenderNode {
    ArticleLanguageNode {
        Objects.requireNonNull(articleId, "articleId");
        Objects.requireNonNull(language, "language");
    }

    @Override
    public String key() {
        return "article:" + articleId + ":lang:" + language;
    }

    @Override
    public int priority() {
        return 60;
    }
}
