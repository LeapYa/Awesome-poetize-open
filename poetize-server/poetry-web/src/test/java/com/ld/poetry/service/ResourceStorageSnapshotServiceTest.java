package com.ld.poetry.service;

import com.ld.poetry.utils.security.FileSecurityValidator;
import com.ld.poetry.utils.storage.StorageCapability;
import com.ld.poetry.utils.storage.StorageDeleteResult;
import com.ld.poetry.utils.storage.StorageReadHandle;
import com.ld.poetry.utils.storage.StorageResourceRef;
import com.ld.poetry.utils.storage.StorageSnapshot;
import com.ld.poetry.utils.storage.StoreService;
import com.ld.poetry.vo.FileVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourceStorageSnapshotServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void capturesCompleteBytesAndCalculatesSha256() throws Exception {
        byte[] bytes = validPngHeader();
        ResourceStorageSnapshotService service = service(1024);
        StoreService store = readableStore(bytes, (long) bytes.length, "image/png");

        Path snapshotPath;
        try (StorageSnapshot snapshot = service.capture(store, resource("image/png", "image.png", null))) {
            snapshotPath = snapshot.path();
            assertThat(snapshot.sha256()).isEqualTo(sha256(bytes));
            assertThat(snapshot.size()).isEqualTo(bytes.length);
            assertThat(Files.readAllBytes(snapshot.path())).isEqualTo(bytes);
        }

        assertThat(snapshotPath).doesNotExist();
    }

    @Test
    void rejectsDeclaredLengthMismatch() {
        byte[] bytes = validPngHeader();
        ResourceStorageSnapshotService service = service(1024);
        StoreService store = readableStore(bytes, (long) bytes.length + 1, "image/png");

        assertThatThrownBy(() -> service.capture(store, resource("image/png", "image.png", null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("声明大小与完整读取大小不一致");
    }

    @Test
    void rejectsHtmlErrorDocumentEvenWhenRecordedAsImage() {
        byte[] bytes = "<!doctype html><html><body>not found</body></html>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ResourceStorageSnapshotService service = service(1024);
        StoreService store = readableStore(bytes, (long) bytes.length, "text/html");

        assertThatThrownBy(() -> service.capture(store, resource("image/png", "image.png", null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTML错误页");
    }

    @Test
    void rejectsContentBeyondConfiguredLimitWithoutKeepingSnapshot() {
        byte[] bytes = new byte[128];
        ResourceStorageSnapshotService service = service(32);
        StoreService store = readableStore(bytes, null, "application/octet-stream");

        assertThatThrownBy(() -> service.capture(store, resource("application/octet-stream", "file.bin", null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("资源快照写入失败");
        assertThat(tempDir).isEmptyDirectory();
    }

    private ResourceStorageSnapshotService service(long maxBytes) {
        return new ResourceStorageSnapshotService(
                new FileSecurityValidator(),
                tempDir.toString(),
                maxBytes
        );
    }

    private StorageResourceRef resource(String mimeType, String originalName, Long size) {
        return new StorageResourceRef(
                1,
                "https://cdn.example.test/image.png",
                "image.png",
                originalName,
                size,
                null,
                mimeType
        );
    }

    private StoreService readableStore(byte[] bytes, Long declaredLength, String contentType) {
        return new StoreService() {
            @Override
            public List<StorageDeleteResult> deleteFiles(List<StorageResourceRef> resources) {
                return List.of();
            }

            @Override
            public FileVO saveFile(FileVO fileVO) {
                throw new UnsupportedOperationException();
            }

            @Override
            public StorageReadHandle openRead(StorageResourceRef resource, long maxBytes) {
                return StorageReadHandle.bounded(
                        new ByteArrayInputStream(bytes),
                        declaredLength,
                        contentType,
                        URI.create(resource.path()),
                        maxBytes
                );
            }

            @Override
            public StorageCapability getCapability() {
                return new StorageCapability(
                        "test",
                        true,
                        true,
                        false,
                        false,
                        false,
                        0,
                        List.of()
                );
            }

            @Override
            public String getStoreName() {
                return "test";
            }
        };
    }

    private byte[] validPngHeader() {
        return new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D,
                0x49, 0x48, 0x44, 0x52
        };
    }

    private String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}