package com.ld.poetry.service;

import com.ld.poetry.utils.storage.StorageRangeReadHandle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalResourceFileServiceTest {

    @TempDir
    private Path tempDir;

    private LocalResourceFileService service;

    @BeforeEach
    void setUp() {
        ResourceAvailabilityService availabilityService = new ResourceAvailabilityService();
        ReflectionTestUtils.setField(availabilityService, "localUploadUrl", tempDir.toString() + "/");
        ReflectionTestUtils.setField(availabilityService, "localDownloadUrl", "/static/");
        ReflectionTestUtils.setField(availabilityService, "staticResourceRoots", "");
        service = new LocalResourceFileService(availabilityService);
        ReflectionTestUtils.setField(service, "localUploadUrl", tempDir.toString() + "/");
    }

    @Test
    void opensExactRangeFromManagedRealFile() throws Exception {
        Path file = tempDir.resolve("assets/media.bin");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "0123456789", StandardCharsets.UTF_8);

        try (StorageRangeReadHandle handle = service.openRange(
                "/static/assets/media.bin",
                2,
                5,
                10,
                "application/octet-stream"
        )) {
            assertThat(handle.contentLength()).isEqualTo(4);
            assertThat(handle.totalLength()).isEqualTo(10);
            assertThat(handle.inputStream().readAllBytes())
                    .isEqualTo("2345".getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    void rejectsFileWhoseLengthChangedAfterVerification() throws Exception {
        Path file = tempDir.resolve("assets/media.bin");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "changed", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service.openRange(
                "/static/assets/media.bin",
                0,
                3,
                10,
                "application/octet-stream"
        ))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("长度与已验证副本元数据不一致");
    }

    @Test
    void rejectsPathTraversalOutsideManagedRoot() {
        assertThatThrownBy(() -> service.openRange(
                "/static/../outside.bin",
                0,
                0,
                1,
                "application/octet-stream"
        ))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("未找到可读取的本地源文件");
    }
}