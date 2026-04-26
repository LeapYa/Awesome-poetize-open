package com.ld.poetry.config;

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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrerenderStartupRunnerTest {

    @Mock
    private PrerenderFacade prerenderFacade;

    @Mock
    private PrerenderService prerenderService;

    private PrerenderStartupRunner runner;

    @BeforeEach
    void setUp() {
        runner = new PrerenderStartupRunner();
        ReflectionTestUtils.setField(runner, "prerenderFacade", prerenderFacade);
        ReflectionTestUtils.setField(runner, "prerenderService", prerenderService);
        ReflectionTestUtils.setField(runner, "prerenderStartupEnabled", true);
        ReflectionTestUtils.setField(runner, "prerenderStartupDelay", 5);
    }

    @Test
    void runShouldScheduleFacadeRebuildWhenTemplateExists() {
        when(prerenderService.isTemplateAvailable()).thenReturn(true);

        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(prerenderFacade).rebuildSiteAsync(Duration.ofSeconds(5));
    }
}
