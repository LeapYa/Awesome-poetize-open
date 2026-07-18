package com.ld.poetry.config;

import com.ld.poetry.service.prerender.PrerenderAssetConsistencyChecker;
import com.ld.poetry.service.prerender.PrerenderAssetConsistencyChecker.ConsistencyResult;
import com.ld.poetry.service.prerender.PrerenderFacade;
import com.ld.poetry.service.prerender.PrerenderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Set;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrerenderStartupRunnerTest {

    @Mock
    private PrerenderFacade prerenderFacade;

    @Mock
    private PrerenderService prerenderService;

    @Mock
    private PrerenderAssetConsistencyChecker consistencyChecker;

    private PrerenderStartupRunner runner;

    @BeforeEach
    void setUp() {
        runner = new PrerenderStartupRunner();
        ReflectionTestUtils.setField(runner, "prerenderFacade", prerenderFacade);
        ReflectionTestUtils.setField(runner, "prerenderService", prerenderService);
        ReflectionTestUtils.setField(runner, "consistencyChecker", consistencyChecker);
        ReflectionTestUtils.setField(runner, "prerenderStartupEnabled", true);
        ReflectionTestUtils.setField(runner, "prerenderStartupDelay", 5);
    }

    @Test
    void runShouldScheduleAsyncRebuildWhenConsistentAndTemplateExists() {
        when(prerenderService.isTemplateAvailable()).thenReturn(true);
        when(consistencyChecker.check()).thenReturn(ConsistencyResult.consistent());

        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(prerenderFacade).rebuildSiteAsync(Duration.ofSeconds(5));
        verify(prerenderFacade, never()).rebuildSite();
    }

    @Test
    void runShouldRebuildSynchronouslyWhenAssetsInconsistent() {
        when(prerenderService.isTemplateAvailable()).thenReturn(true);
        when(consistencyChecker.check()).thenReturn(
                new ConsistencyResult(3, Set.of("/static/pb.oldhash.js", "/static/css/missing.css")));

        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(prerenderFacade).rebuildSite();
        verify(prerenderFacade, never()).rebuildSiteAsync(Duration.ofSeconds(5));
    }

    @Test
    void runShouldFallbackToAsyncRebuildWhenSyncRebuildFails() {
        when(prerenderService.isTemplateAvailable()).thenReturn(true);
        when(consistencyChecker.check()).thenReturn(
                new ConsistencyResult(3, Set.of("/static/pb.oldhash.js")));
        doThrow(new RuntimeException("boom")).when(prerenderFacade).rebuildSite();

        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(prerenderFacade).rebuildSite();
        verify(prerenderFacade).rebuildSiteAsync(Duration.ofSeconds(5));
    }
}
