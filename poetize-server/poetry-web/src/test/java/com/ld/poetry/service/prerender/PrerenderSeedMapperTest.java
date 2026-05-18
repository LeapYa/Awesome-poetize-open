package com.ld.poetry.service.prerender;

import com.ld.poetry.dao.LabelMapper;
import com.ld.poetry.event.ArticleSavedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class PrerenderSeedMapperTest {

    @Mock
    private LabelMapper labelMapper;

    @InjectMocks
    private PrerenderSeedMapper seedMapper;

    @Test
    void articleUpdateShouldIncludeAllAffectedSeeds() {
        ArticleSavedEvent event = new ArticleSavedEvent(42, 2, 9, 1, 7, null, true, "UPDATE", false, null);

        PrerenderRequest request = seedMapper.forArticleEvent(event);

        assertTrue(request.seeds().contains(new StaticPageNode(PrerenderStaticPage.HOME)));
        assertTrue(request.seeds().contains(new SortIndexNode()));
        assertTrue(request.seeds().contains(new SortNode(2)));
        assertTrue(request.seeds().contains(new SortNode(1)));
        assertTrue(request.seeds().contains(new SortLabelNode(2, 9)));
        assertTrue(request.seeds().contains(new SortLabelNode(1, 7)));
        assertTrue(request.seeds().contains(new ArticleNode(42)));
    }

    @Test
    void sortDeletionShouldCleanupWholeSortDirectory() {
        PrerenderRequest request = seedMapper.forSortDeletion(8);

        assertTrue(request.cleanups().contains(PrerenderCleanup.category(8, null)));
        assertTrue(request.seeds().contains(new StaticPageNode(PrerenderStaticPage.HOME)));
        assertTrue(request.seeds().contains(new SortIndexNode()));
    }
}
