package com.ld.poetry.controller;

import com.ld.poetry.dao.LabelMapper;
import com.ld.poetry.dao.SortMapper;
import com.ld.poetry.entity.Label;
import com.ld.poetry.service.SitemapService;
import com.ld.poetry.service.prerender.PrerenderFacade;
import com.ld.poetry.utils.CommonQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SortLabelControllerTest {

    @Mock
    private SortMapper sortMapper;

    @Mock
    private LabelMapper labelMapper;

    @Mock
    private CommonQuery commonQuery;

    @Mock
    private PrerenderFacade prerenderFacade;

    @Mock
    private com.ld.poetry.service.ai.rag.RagSyncService ragSyncService;

    @Mock
    private SitemapService sitemapService;

    private SortLabelController controller;

    @BeforeEach
    void setUp() {
        controller = new SortLabelController();
        ReflectionTestUtils.setField(controller, "sortMapper", sortMapper);
        ReflectionTestUtils.setField(controller, "labelMapper", labelMapper);
        ReflectionTestUtils.setField(controller, "commonQuery", commonQuery);
        ReflectionTestUtils.setField(controller, "prerenderFacade", prerenderFacade);
        ReflectionTestUtils.setField(controller, "ragSyncService", ragSyncService);
        ReflectionTestUtils.setField(controller, "sitemapService", sitemapService);
    }

    @Test
    void saveLabelShouldDelegateToFacade() {
        when(labelMapper.insert(any(Label.class))).thenAnswer(invocation -> {
            Label saved = invocation.getArgument(0);
            saved.setId(12);
            return 1;
        });

        Label label = new Label();
        label.setSortId(3);
        label.setLabelName("Java");
        label.setLabelDescription("Java articles");

        controller.saveLabel(label);

        verify(prerenderFacade).refreshLabelHierarchy(3, 12, null);
    }

    @Test
    void deleteLabelShouldDelegateCleanupToFacade() {
        Label existing = new Label();
        existing.setId(12);
        existing.setSortId(3);
        when(labelMapper.selectById(12)).thenReturn(existing);

        controller.deleteLabel(12);

        verify(prerenderFacade).deleteLabelHierarchy(3, 12);
    }

    @Test
    void updateLabelShouldRefreshOldAndNewSortPathsThroughFacade() {
        Label existing = new Label();
        existing.setId(12);
        existing.setSortId(3);
        when(labelMapper.selectById(12)).thenReturn(existing);

        Label label = new Label();
        label.setId(12);
        label.setSortId(5);
        label.setLabelName("Backend");
        label.setLabelDescription("Backend label");

        controller.updateLabel(label);

        verify(prerenderFacade).refreshLabelHierarchy(5, 12, 3);
    }
}
