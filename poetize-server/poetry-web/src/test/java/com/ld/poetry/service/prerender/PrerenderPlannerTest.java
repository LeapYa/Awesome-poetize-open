package com.ld.poetry.service.prerender;

import com.ld.poetry.entity.Label;
import com.ld.poetry.entity.Sort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrerenderPlannerTest {

    private final PrerenderPlanner planner = new PrerenderPlanner(new PrerenderGraphResolver());

    @Test
    void fullSitePlanShouldExpandCatalogGraphDepthFirstAndKeepRenderableNodes() {
        Sort sort = new Sort();
        sort.setId(1);
        Label label = new Label();
        label.setId(11);
        sort.setLabels(List.of(label));

        PrerenderSnapshot snapshot = new PrerenderSnapshot("zh", List.of(sort), List.of(42), Map.of(42, List.of("zh", "en")));
        PrerenderRequest request = new PrerenderRequest("full", Set.of(new SiteRootNode()), List.of(), false);

        PrerenderPlan plan = planner.plan(request, snapshot);

        assertEquals(16, plan.renderNodes().size());
        assertTrue(plan.renderNodes().contains(new StaticPageNode(PrerenderStaticPage.HOME)));
        assertTrue(plan.renderNodes().contains(new SortIndexNode()));
        assertTrue(plan.renderNodes().contains(new SortNode(1)));
        assertTrue(plan.renderNodes().contains(new SortLabelNode(1, 11)));
        assertTrue(plan.renderNodes().contains(new ArticleLanguageNode(42, "zh")));
        assertTrue(plan.renderNodes().contains(new ArticleLanguageNode(42, "en")));
        assertFalse(plan.renderNodes().contains(new ArticleNode(42)));
    }
}
