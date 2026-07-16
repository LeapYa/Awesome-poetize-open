package com.ld.poetry.utils.storage;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrustedRemoteStorageReaderRangeTest {

    private static final byte[] CONTENT = "0123456789".getBytes(StandardCharsets.UTF_8);

    private TrustedRemoteStorageReader reader;
    private HttpServer httpServer;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        reader = new TrustedRemoteStorageReader();
        ReflectionTestUtils.setField(reader, "allowPrivateHosts", true);
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/range", exchange -> {
            String range = exchange.getRequestHeaders().getFirst("Range");
            if (!"bytes=2-5".equals(range)) {
                exchange.sendResponseHeaders(416, -1);
                exchange.close();
                return;
            }
            byte[] body = "2345".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
            exchange.getResponseHeaders().set("Content-Range", "bytes 2-5/10");
            exchange.sendResponseHeaders(206, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        httpServer.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().set("Location", "/range");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        httpServer.createContext("/ignored-range", exchange -> {
            exchange.sendResponseHeaders(200, CONTENT.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(CONTENT);
            }
        });
        httpServer.createContext("/wrong-range", exchange -> {
            byte[] body = "1234".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Range", "bytes 1-4/10");
            exchange.sendResponseHeaders(206, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        httpServer.start();
        baseUrl = "http://127.0.0.1:" + httpServer.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    @Test
    void opensExactTrustedRangeAcrossValidatedRedirect() throws Exception {
        try (StorageRangeReadHandle handle = reader.openRange(
                baseUrl + "/redirect",
                Set.of(baseUrl),
                2,
                5
        )) {
            assertThat(handle.contentLength()).isEqualTo(4);
            assertThat(handle.totalLength()).isEqualTo(10);
            assertThat(handle.inputStream().readAllBytes()).isEqualTo("2345".getBytes(StandardCharsets.UTF_8));
            assertThat(handle.resolvedUri().getPath()).isEqualTo("/range");
        }
    }

    @Test
    void rejectsServerThatIgnoresRangeRequest() {
        assertThatThrownBy(() -> reader.openRange(
                baseUrl + "/ignored-range",
                Set.of(baseUrl),
                2,
                5
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP 206");
    }

    @Test
    void rejectsMismatchedContentRange() {
        assertThatThrownBy(() -> reader.openRange(
                baseUrl + "/wrong-range",
                Set.of(baseUrl),
                2,
                5
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("与请求不一致");
    }
}