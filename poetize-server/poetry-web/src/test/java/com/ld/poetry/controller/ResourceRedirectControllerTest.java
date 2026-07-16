package com.ld.poetry.controller;

import com.ld.poetry.dao.ResourceRedirectMapper;
import com.ld.poetry.entity.ResourceRedirect;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceRedirectControllerTest {

    @Mock
    private ResourceRedirectMapper resourceRedirectMapper;

    private ResourceRedirectController controller;

    @BeforeEach
    void setUp() {
        controller = new ResourceRedirectController(resourceRedirectMapper);
    }

    @Test
    void exposesOnlyDedicatedLookupEndpoint() throws Exception {
        GetMapping mapping = ResourceRedirectController.class
                .getMethod("redirect", String.class, String.class, HttpServletResponse.class)
                .getAnnotation(GetMapping.class);

        assertThat(mapping.value()).containsExactly("/resource/redirect");
    }

    @Test
    void redirectsOnlyFromPersistedStaticPathMapping() throws Exception {
        String sourcePath = "/static/articlePicture/hello world.png";
        ResourceRedirect redirect = redirect(sourcePath, "https://image.example.com/hello-world.png");
        when(resourceRedirectMapper.findActiveBySourcePath(sourcePath)).thenReturn(redirect);

        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.redirect("/static/articlePicture/hello%20world.png?version=1", null, response);

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getHeader("Location")).isEqualTo("https://image.example.com/hello-world.png");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("public, max-age=300");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        verify(resourceRedirectMapper).findActiveBySourcePath(sourcePath);
    }

    @Test
    void preservesLiteralPlusInStaticPath() throws Exception {
        String sourcePath = "/static/articlePicture/a+b.png";
        when(resourceRedirectMapper.findActiveBySourcePath(sourcePath))
                .thenReturn(redirect(sourcePath, "https://image.example.com/a-plus-b.png"));

        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.redirect(sourcePath, null, response);

        assertThat(response.getStatus()).isEqualTo(302);
        verify(resourceRedirectMapper).findActiveBySourcePath(sourcePath);
    }

    @Test
    void acceptsExplicitPathParameterWhenInternalHeaderIsAbsent() throws Exception {
        String sourcePath = "/static/articlePicture/fallback.png";
        when(resourceRedirectMapper.findActiveBySourcePath(sourcePath))
                .thenReturn(redirect(sourcePath, "https://image.example.com/fallback.png"));

        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.redirect(null, sourcePath, response);

        assertThat(response.getStatus()).isEqualTo(302);
        verify(resourceRedirectMapper).findActiveBySourcePath(sourcePath);
    }

    @Test
    void returnsNotFoundWhenMappingDoesNotExist() throws Exception {
        String sourcePath = "/static/articlePicture/missing.png";
        when(resourceRedirectMapper.findActiveBySourcePath(sourcePath)).thenReturn(null);

        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.redirect(sourcePath, null, response);

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getHeader("Location")).isNull();
    }

    @Test
    void rejectsUnsafePersistedTargetUrl() throws Exception {
        String sourcePath = "/static/articlePicture/unsafe.png";
        when(resourceRedirectMapper.findActiveBySourcePath(sourcePath))
                .thenReturn(redirect(sourcePath, "javascript:alert(1)"));

        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.redirect(sourcePath, null, response);

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getHeader("Location")).isNull();
    }

    @Test
    void rejectsDecodedPathTraversalBeforeDatabaseLookup() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.redirect("/static/articlePicture/%2e%2e/secret.txt", null, response);

        assertThat(response.getStatus()).isEqualTo(404);
        verify(resourceRedirectMapper, never()).findActiveBySourcePath(org.mockito.ArgumentMatchers.anyString());
    }

    private ResourceRedirect redirect(String sourcePath, String targetUrl) {
        ResourceRedirect redirect = new ResourceRedirect();
        redirect.setSourcePath(sourcePath);
        redirect.setTargetUrl(targetUrl);
        redirect.setStatus(true);
        return redirect;
    }
}