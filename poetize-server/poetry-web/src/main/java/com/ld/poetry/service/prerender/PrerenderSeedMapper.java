package com.ld.poetry.service.prerender;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.ld.poetry.dao.LabelMapper;
import com.ld.poetry.entity.Label;
import com.ld.poetry.event.ArticleSavedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
class PrerenderSeedMapper {

    private final LabelMapper labelMapper;

    PrerenderRequest forFullSiteRebuild() {
        return new PrerenderRequest("全站预渲染重建", Set.of(new SiteRootNode()), List.of(), true);
    }

    PrerenderRequest forFriendsPage() {
        return request("友人帐页面刷新", List.of(new StaticPageNode(PrerenderStaticPage.FRIENDS)), List.of());
    }

    PrerenderRequest forFavoritesPage() {
        return request("收藏夹页面刷新", List.of(new StaticPageNode(PrerenderStaticPage.FAVORITES)), List.of());
    }

    PrerenderRequest forSortRefresh(Integer sortId) {
        LinkedHashSet<PrerenderNode> seeds = new LinkedHashSet<>();
        seeds.add(new StaticPageNode(PrerenderStaticPage.HOME));
        seeds.add(new SortIndexNode());
        addSortNode(seeds, sortId);
        addAllLabelNodesForSort(seeds, sortId);
        return request("分类刷新: " + sortId, seeds, List.of());
    }

    PrerenderRequest forSortDeletion(Integer sortId) {
        LinkedHashSet<PrerenderNode> seeds = new LinkedHashSet<>();
        seeds.add(new StaticPageNode(PrerenderStaticPage.HOME));
        seeds.add(new SortIndexNode());
        List<PrerenderCleanup> cleanups = sortId == null ? List.of() : List.of(PrerenderCleanup.category(sortId, null));
        return request("分类删除: " + sortId, seeds, cleanups);
    }

    PrerenderRequest forLabelRefresh(Integer currentSortId, Integer labelId, Integer previousSortId) {
        LinkedHashSet<PrerenderNode> seeds = new LinkedHashSet<>();
        seeds.add(new SortIndexNode());
        addSortNode(seeds, currentSortId);
        addSortNode(seeds, previousSortId);
        addLabelNode(seeds, currentSortId, labelId);
        if (previousSortId != null && !previousSortId.equals(currentSortId)) {
            addAllLabelNodesForSort(seeds, previousSortId);
        }

        List<PrerenderCleanup> cleanups = new ArrayList<>();
        if (previousSortId != null && labelId != null && !previousSortId.equals(currentSortId)) {
            cleanups.add(PrerenderCleanup.category(previousSortId, labelId));
        }
        return request("标签刷新: " + labelId, seeds, cleanups);
    }

    PrerenderRequest forLabelDeletion(Integer sortId, Integer labelId) {
        LinkedHashSet<PrerenderNode> seeds = new LinkedHashSet<>();
        seeds.add(new SortIndexNode());
        addSortNode(seeds, sortId);

        List<PrerenderCleanup> cleanups = new ArrayList<>();
        if (sortId != null && labelId != null) {
            cleanups.add(PrerenderCleanup.category(sortId, labelId));
        }
        return request("标签删除: " + labelId, seeds, cleanups);
    }

    PrerenderRequest forArticleEvent(ArticleSavedEvent event) {
        if (event == null || event.getArticleId() == null || event.getOperationType() == null) {
            return request("空文章预渲染请求", List.of(), List.of());
        }

        return switch (event.getOperationType()) {
            case "CREATE" -> Boolean.TRUE.equals(event.getViewStatus())
                    ? buildVisibleArticleRequest("文章创建", event, List.of())
                    : request("不可见文章创建，跳过预渲染", List.of(), List.of());
            case "UPDATE" -> Boolean.TRUE.equals(event.getViewStatus())
                    ? buildVisibleArticleRequest("文章更新", event, List.of())
                    : buildDeleteLikeArticleRequest("文章隐藏", event);
            case "DELETE" -> buildDeleteLikeArticleRequest("文章删除", event);
            default -> request("未知文章事件: " + event.getOperationType(), List.of(), List.of());
        };
    }

    private PrerenderRequest buildVisibleArticleRequest(String action, ArticleSavedEvent event, List<PrerenderCleanup> extraCleanups) {
        LinkedHashSet<PrerenderNode> seeds = new LinkedHashSet<>();
        seeds.add(new StaticPageNode(PrerenderStaticPage.HOME));
        seeds.add(new SortIndexNode());
        addSortNode(seeds, event.getSortId());
        addSortNode(seeds, event.getPreviousSortId());
        addLabelNode(seeds, event.getSortId(), event.getLabelId());
        addLabelNode(seeds, event.getPreviousSortId(), event.getPreviousLabelId());
        seeds.add(new ArticleNode(event.getArticleId()));
        return request(action + ": " + event.getArticleId(), seeds, extraCleanups);
    }

    private PrerenderRequest buildDeleteLikeArticleRequest(String action, ArticleSavedEvent event) {
        List<PrerenderCleanup> cleanups = List.of(PrerenderCleanup.article(event.getArticleId()));
        LinkedHashSet<PrerenderNode> seeds = new LinkedHashSet<>();
        seeds.add(new StaticPageNode(PrerenderStaticPage.HOME));
        seeds.add(new SortIndexNode());
        addSortNode(seeds, event.getSortId());
        addSortNode(seeds, event.getPreviousSortId());
        addLabelNode(seeds, event.getSortId(), event.getLabelId());
        addLabelNode(seeds, event.getPreviousSortId(), event.getPreviousLabelId());
        return request(action + ": " + event.getArticleId(), seeds, cleanups);
    }

    private void addSortNode(Set<PrerenderNode> seeds, Integer sortId) {
        if (sortId != null) {
            seeds.add(new SortNode(sortId));
        }
    }

    private void addLabelNode(Set<PrerenderNode> seeds, Integer sortId, Integer labelId) {
        if (sortId != null && labelId != null) {
            seeds.add(new SortLabelNode(sortId, labelId));
        }
    }

    private void addAllLabelNodesForSort(Set<PrerenderNode> seeds, Integer sortId) {
        if (sortId == null) {
            return;
        }
        List<Label> labels = new LambdaQueryChainWrapper<>(labelMapper)
                .eq(Label::getSortId, sortId)
                .orderByAsc(Label::getLabelName)
                .list();
        for (Label label : labels) {
            if (label != null && label.getId() != null) {
                seeds.add(new SortLabelNode(sortId, label.getId()));
            }
        }
    }

    private PrerenderRequest request(String description, List<PrerenderNode> seeds, List<PrerenderCleanup> cleanups) {
        return request(description, new LinkedHashSet<>(seeds), cleanups);
    }

    private PrerenderRequest request(String description, Set<PrerenderNode> seeds, List<PrerenderCleanup> cleanups) {
        return new PrerenderRequest(description, seeds, cleanups, false);
    }
}
