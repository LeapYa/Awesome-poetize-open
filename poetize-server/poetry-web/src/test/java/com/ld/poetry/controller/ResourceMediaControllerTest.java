package com.ld.poetry.controller;

import com.ld.poetry.service.ResourceMediaAccessException;
import com.ld.poetry.service.ResourceMediaService;
import com.ld.poetry.utils.storage.StorageClientAccess;
import com.ld.poetry.utils.storage.StorageRangeReadHandle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceMediaControllerTest {

    private static final String PUBLIC_ID = "0123456789abcdef0123456789abcdef";
    private static final String HASH = "a".repeat(64);
    private static final byte[] CONTENT = "0123456789".getBytes(StandardCharsets.UTF_8);

    @Mock
    private ResourceMediaService resourceMediaService;

    private ResourceMediaController controller;

    @BeforeEach
    void setUp() {
        controller = new ResourceMediaController(resourceMediaService);
    }

    @Test
    void localGetShouldReturnStableHeadersAndCompleteBody() throws Exception {
        ResourceMediaService.MediaDescriptor descriptor = descriptor("local");
        when(resourceMediaService.resolve(PUBLIC_ID)).thenReturn(descriptor);
        when(resourceMediaService.openRange(descriptor, 0, 9))
                .thenReturn(rangeHandle(CONTENT, 10));

        MockHttpServletRequest request = request("GET");
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.serve(PUBLIC_ID, request, response);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsByteArray()).isEqualTo(CONTENT);
        assertThat(response.getHeader(HttpHeaders.ETAG)).isEqualTo(descriptor.etag());
        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL))
                .isEqualTo("public, max-age=0, must-revalidate");
        assertThat(response.getHeader(HttpHeaders.ACCEPT_RANGES)).isEqualTo("bytes");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getContentType()).isEqualTo("image/png");
        assertThat(response.getContentLengthLong()).isEqualTo(10);
    }

    @Test
    void rangeGetShouldReturnOnlyRequestedBytes() throws Exception {
        ResourceMediaService.MediaDescriptor descriptor = descriptor("local");
        when(resourceMediaService.resolve(PUBLIC_ID)).thenReturn(descriptor);
        when(resourceMediaService.openRange(descriptor, 2, 5))
                .thenReturn(rangeHandle("2345".getBytes(StandardCharsets.UTF_8), 10));

        MockHttpServletRequest request = request("GET");
        request.addHeader(HttpHeaders.RANGE, "bytes=2-5");
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.serve(PUBLIC_ID, request, response);

        assertThat(response.getStatus()).isEqualTo(206);
        assertThat(response.getHeader(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 2-5/10");
        assertThat(response.getContentLengthLong()).isEqualTo(4);
        assertThat(response.getContentAsString()).isEqualTo("2345");
    }

    @Test
    void suffixRangeShouldResolveAgainstTrustedTotalLength() throws Exception {
        ResourceMediaService.MediaDescriptor descriptor = descriptor("local");
        when(resourceMediaService.resolve(PUBLIC_ID)).thenReturn(descriptor);
        when(resourceMediaService.openRange(descriptor, 7, 9))
                .thenReturn(rangeHandle("789".getBytes(StandardCharsets.UTF_8), 10));

        MockHttpServletRequest request = request("GET");
        request.addHeader(HttpHeaders.RANGE, "bytes=-3");
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.serve(PUBLIC_ID, request, response);

        assertThat(response.getStatus()).isEqualTo(206);
        assertThat(response.getHeader(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 7-9/10");
        assertThat(response.getContentAsString()).isEqualTo("789");
    }

    @Test
    void matchingIfNoneMatchShouldReturnNotModifiedWithoutPhysicalRead() throws Exception {
        ResourceMediaService.MediaDescriptor descriptor = descriptor("local");
        when(resourceMediaService.resolve(PUBLIC_ID)).thenReturn(descriptor);

        MockHttpServletRequest request = request("GET");
        request.addHeader(HttpHeaders.IF_NONE_MATCH, "W/" + descriptor.etag());
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.serve(PUBLIC_ID, request, response);

        assertThat(response.getStatus()).isEqualTo(304);
        assertThat(response.getContentAsByteArray()).isEmpty();
        verify(resourceMediaService, never()).openRange(any(), any(Long.class), any(Long.class));
    }

    @Test
    void mismatchedIfRangeShouldIgnoreRangeAndReturnCompleteRepresentation() throws Exception {
        ResourceMediaService.MediaDescriptor descriptor = descriptor("local");
        when(resourceMediaService.resolve(PUBLIC_ID)).thenReturn(descriptor);
        when(resourceMediaService.openRange(descriptor, 0, 9))
                .thenReturn(rangeHandle(CONTENT, 10));

        MockHttpServletRequest request = request("GET");
        request.addHeader(HttpHeaders.RANGE, "bytes=2-5");
        request.addHeader(HttpHeaders.IF_RANGE, "\"different\"");
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.serve(PUBLIC_ID, request, response);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.CONTENT_RANGE)).isNull();
        assertThat(response.getContentAsByteArray()).isEqualTo(CONTENT);
    }

    @Test
    void multipleRangesShouldReturnRangeNotSatisfiable() throws Exception {
        ResourceMediaService.MediaDescriptor descriptor = descriptor("local");
        when(resourceMediaService.resolve(PUBLIC_ID)).thenReturn(descriptor);

        MockHttpServletRequest request = request("GET");
        request.addHeader(HttpHeaders.RANGE, "bytes=0-1,4-5");
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.serve(PUBLIC_ID, request, response);

        assertThat(response.getStatus()).isEqualTo(416);
        assertThat(response.getHeader(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes */10");
        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL))
                .isEqualTo("private, no-store, max-age=0");
        verify(resourceMediaService, never()).openRange(any(), any(Long.class), any(Long.class));
    }

    @Test
    void headShouldVerifyReadableSourceWithoutWritingBody() throws Exception {
        ResourceMediaService.MediaDescriptor descriptor = descriptor("local");
        when(resourceMediaService.resolve(PUBLIC_ID)).thenReturn(descriptor);
        when(resourceMediaService.openRange(descriptor, 0, 0))
                .thenReturn(rangeHandle(new byte[]{'0'}, 10));

        MockHttpServletRequest request = request("HEAD");
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.serve(PUBLIC_ID, request, response);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentLengthLong()).isEqualTo(10);
        assertThat(response.getContentAsByteArray()).isEmpty();
        verify(resourceMediaService).openRange(descriptor, 0, 0);
    }

    @Test
    void remoteGetShouldUseControlledTemporaryRedirectWithoutCachingPrivateUrl() throws Exception {
        ResourceMediaService.MediaDescriptor descriptor = descriptor("qiniu");
        when(resourceMediaService.resolve(PUBLIC_ID)).thenReturn(descriptor);
        when(resourceMediaService.resolveClientAccess(descriptor)).thenReturn(
                new StorageClientAccess("https://cdn.example.com/image.png?token=secret", 0, true)
        );

        MockHttpServletRequest request = request("GET");
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.serve(PUBLIC_ID, request, response);

        assertThat(response.getStatus()).isEqualTo(307);
        assertThat(response.getHeader(HttpHeaders.LOCATION))
                .isEqualTo("https://cdn.example.com/image.png?token=secret");
        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL))
                .isEqualTo("private, no-store, max-age=0");
        verify(resourceMediaService, never()).openRange(any(), any(Long.class), any(Long.class));
    }

    @Test
    void pendingReplacementShouldReturnRetryableUnavailable() throws Exception {
        when(resourceMediaService.resolve(PUBLIC_ID)).thenThrow(new ResourceMediaAccessException(
                ResourceMediaAccessException.Reason.TEMPORARILY_UNAVAILABLE,
                "资源内容替换尚未完成"
        ));

        MockHttpServletRequest request = request("GET");
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.serve(PUBLIC_ID, request, response);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("5");
        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL))
                .isEqualTo("private, no-store, max-age=0");
    }

    private MockHttpServletRequest request(String method) {
        return new MockHttpServletRequest(method, "/media/" + PUBLIC_ID);
    }

    private ResourceMediaService.MediaDescriptor descriptor(String storeType) {
        LocalDateTime verifiedAt = LocalDateTime.of(2026, 7, 14, 1, 2, 3);
        return new ResourceMediaService.MediaDescriptor(
                1,
                PUBLIC_ID,
                21L,
                3,
                storeType,
                "assets/image.png",
                "local".equals(storeType)
                        ? "/static/assets/image.png"
                        : "https://cdn.example.com/image.png",
                HASH,
                10,
                "image/png",
                "image.png",
                verifiedAt,
                verifiedAt
        );
    }

    private StorageRangeReadHandle rangeHandle(byte[] bytes, long totalLength) {
        return StorageRangeReadHandle.bounded(
                new ByteArrayInputStream(bytes),
                bytes.length,
                totalLength,
                "image/png",
                URI.create("file:///tmp/image.png")
        );
    }
}