package com.ld.poetry.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ld.poetry.entity.Resource;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceAvailabilityServiceTest {

    @TempDir
    private Path tempDir;

    private ResourceAvailabilityService service;

    private HttpServer httpServer;

    @BeforeEach
    void setUp() {
        service = new ResourceAvailabilityService();
        ReflectionTestUtils.setField(service, "localUploadUrl", tempDir.toString() + "/");
        ReflectionTestUtils.setField(service, "localDownloadUrl", "/static/");
        ReflectionTestUtils.setField(service, "staticResourceRoots", "");
    }

    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    @Test
    void localFileAvailabilityShouldDriveInvalidResourcePagination() throws IOException {
        Files.createDirectories(tempDir.resolve("assets"));
        Files.write(tempDir.resolve("assets/existing.png"), pngHeader());

        Resource missingNew = resource(1, "/static/assets/missing-new.png");
        Resource existing = resource(2, "/static/assets/existing.png");
        Resource missingOld = resource(3, "assets/missing-old.png");

        Page<Resource> firstPage = service.buildInvalidResourcePage(List.of(missingNew, existing, missingOld), 1, 1);
        Page<Resource> secondPage = service.buildInvalidResourcePage(List.of(missingNew, existing, missingOld), 2, 1);

        assertThat(firstPage.getTotal()).isEqualTo(2);
        assertThat(firstPage.getRecords()).containsExactly(missingNew);
        assertThat(secondPage.getTotal()).isEqualTo(2);
        assertThat(secondPage.getRecords()).containsExactly(missingOld);
    }

    @Test
    void builtInStaticResourceShouldBeValidWhenUploadDirectoryMissesIt() throws IOException {
        Path uploadRoot = tempDir.resolve("uploads");
        Path staticRoot = tempDir.resolve("public/static");
        Files.createDirectories(staticRoot.resolve("assets"));
        Files.write(staticRoot.resolve("backgroundPicture.jpg"), jpegHeader());
        Files.write(staticRoot.resolve("admireImage.jpg"), "/* 样式文件 */".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Files.write(staticRoot.resolve("assets/poetize.jpg"), jpegHeader());

        ReflectionTestUtils.setField(service, "localUploadUrl", uploadRoot.toString() + "/");
        ReflectionTestUtils.setField(service, "staticResourceRoots", staticRoot.toString());

        Resource validBuiltIn = resource(1, "/static/assets/backgroundPicture.jpg");
        Resource validStaticBuiltIn = resource(2, "/static/assets/poetize.jpg");
        Resource invalidPseudoImage = resource(3, "/static/assets/admireImage.jpg");

        Page<Resource> page = service.buildInvalidResourcePage(List.of(validBuiltIn, validStaticBuiltIn, invalidPseudoImage), 1, 10);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords()).containsExactly(invalidPseudoImage);
    }

    @Test
    void remoteAvailabilityShouldHonorStatusAndHeadFallback() throws IOException {
        startHttpServer();
        String baseUrl = "http://127.0.0.1:" + httpServer.getAddress().getPort();

        Resource ok = resource(1, baseUrl + "/ok");
        Resource redirect = resource(2, baseUrl + "/redirect");
        Resource headRejectedButGetOk = resource(3, baseUrl + "/head-reject-get-ok");
        Resource missing = resource(4, baseUrl + "/missing");
        Resource invalidUrl = resource(5, "http://%");

        Page<Resource> page = service.buildInvalidResourcePage(
                List.of(ok, redirect, headRejectedButGetOk, missing, invalidUrl), 1, 10);

        assertThat(page.getTotal()).isEqualTo(2);
        assertThat(page.getRecords()).containsExactly(missing, invalidUrl);
    }

    @Test
    void dataUrlShouldBeValidAndBlobUrlShouldBeInvalid() {
        Resource data = resource(1, "data:image/png;base64,AA==");
        Resource blob = resource(2, "blob:http://example.com/123");

        Page<Resource> page = service.buildInvalidResourcePage(List.of(data, blob), 1, 10);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords()).containsExactly(blob);
    }

    private void startHttpServer() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/ok", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        httpServer.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().set("Location", "/ok");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        httpServer.createContext("/head-reject-get-ok", exchange -> {
            if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }
            byte[] body = new byte[]{1};
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        httpServer.createContext("/missing", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        httpServer.start();
    }

    private Resource resource(Integer id, String path) {
        Resource resource = new Resource();
        resource.setId(id);
        resource.setPath(path);
        resource.setCreateTime(LocalDateTime.now().minusMinutes(id));
        return resource;
    }

    private byte[] pngHeader() {
        return new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    }

    private byte[] jpegHeader() {
        return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
    }
}
