package com.ld.poetry.service;

import com.ld.poetry.entity.Resource;
import com.ld.poetry.entity.ResourceLocation;
import com.ld.poetry.utils.security.FileSecurityValidator;
import com.ld.poetry.utils.storage.FileStorageService;
import com.ld.poetry.utils.storage.StorageCapability;
import com.ld.poetry.utils.storage.StorageDeleteResult;
import com.ld.poetry.utils.storage.StorageResourceRef;
import com.ld.poetry.utils.storage.StorageSnapshot;
import com.ld.poetry.utils.storage.StoreService;
import com.ld.poetry.vo.FileVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManagedResourceUploadServiceTest {

    private static final byte[] CONTENT = "managed-upload-content".getBytes(StandardCharsets.UTF_8);
    private static final String STORE_TYPE = "local";
    private static final String STORAGE_KEY = "managed/file.txt";
    private static final String ACCESS_PATH = "/static/managed/file.txt";

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private ResourceStorageSnapshotService snapshotService;

    @Mock
    private ResourceLocationService resourceLocationService;

    @Mock
    private FileSecurityValidator fileSecurityValidator;

    @Mock
    private StoreService storeService;

    @TempDir
    private Path tempDir;

    private ManagedResourceUploadService service;

    @BeforeEach
    void setUp() {
        service = new ManagedResourceUploadService(
                fileStorageService,
                snapshotService,
                resourceLocationService,
                fileSecurityValidator
        );
    }

    @Test
    void registersOnlyAfterCompleteReadBackAndReturnsStablePath() throws Exception {
        FileVO request = request();
        String hash = sha256(CONTENT);
        prepareStore(request, saved(false, hash));
        when(snapshotService.capture(eq(storeService), any())).thenReturn(snapshot(hash, CONTENT));

        Resource resource = new Resource();
        resource.setPath("/media/public-id");
        resource.setType("articleFile");
        resource.setOriginalName("file.txt");
        resource.setSize(CONTENT.length);
        resource.setMimeType("text/plain");
        ResourceLocation location = new ResourceLocation();
        when(resourceLocationService.registerVerifiedUpload(any())).thenReturn(
                new ResourceLocationService.RegisteredUpload(resource, location, false)
        );

        ManagedResourceUploadService.ManagedUploadResult result = service.upload(request, 7);

        assertThat(result.stablePath()).isEqualTo("/media/public-id");
        assertThat(result.reused()).isFalse();
        assertThat(request.getCreateOnly()).isTrue();
        assertThat(request.getResourceHash()).isEqualTo(hash);

        ArgumentCaptor<ResourceLocationService.VerifiedUpload> uploadCaptor =
                ArgumentCaptor.forClass(ResourceLocationService.VerifiedUpload.class);
        verify(resourceLocationService).registerVerifiedUpload(uploadCaptor.capture());
        ResourceLocationService.VerifiedUpload upload = uploadCaptor.getValue();
        assertThat(upload.userId()).isEqualTo(7);
        assertThat(upload.storeType()).isEqualTo(STORE_TYPE);
        assertThat(upload.storageKey()).isEqualTo(STORAGE_KEY);
        assertThat(upload.accessPath()).isEqualTo(ACCESS_PATH);
        assertThat(upload.contentHash()).isEqualTo(hash);
        assertThat(upload.size()).isEqualTo(CONTENT.length);
        verify(storeService, never()).deleteFiles(any());
    }

    @Test
    void hashMismatchRejectsRegistrationAndDeletesOnlyCreatedTarget() throws Exception {
        FileVO request = request();
        String sourceHash = sha256(CONTENT);
        String targetHash = "b".repeat(64);
        prepareStore(request, saved(false, sourceHash));
        when(snapshotService.capture(eq(storeService), any())).thenReturn(snapshot(targetHash, CONTENT));
        prepareSuccessfulDelete();

        assertThatThrownBy(() -> service.upload(request, 7))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("完整回读SHA-256不一致");

        verify(resourceLocationService, never()).registerVerifiedUpload(any());
        assertCompensatedTarget(sourceHash);
    }

    @Test
    void registrationFailureDeletesOnlyCreatedTarget() throws Exception {
        FileVO request = request();
        String hash = sha256(CONTENT);
        prepareStore(request, saved(false, hash));
        when(snapshotService.capture(eq(storeService), any())).thenReturn(snapshot(hash, CONTENT));
        when(resourceLocationService.registerVerifiedUpload(any()))
                .thenThrow(new IllegalStateException("数据库登记失败"));
        prepareSuccessfulDelete();

        assertThatThrownBy(() -> service.upload(request, 7))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("数据库登记失败");

        assertCompensatedTarget(hash);
    }

    @Test
    void adapterReuseIsRejectedWithoutDeletingExistingObject() throws Exception {
        FileVO request = request();
        String hash = sha256(CONTENT);
        prepareStore(request, saved(true, hash));

        assertThatThrownBy(() -> service.upload(request, 7))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不接受存储适配器复用已有对象");

        verify(snapshotService, never()).capture(any(), any());
        verify(resourceLocationService, never()).registerVerifiedUpload(any());
        verify(storeService, never()).deleteFiles(any());
    }

    private FileVO request() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "file.txt",
                "text/plain",
                CONTENT
        );
        FileVO request = new FileVO();
        request.setFile(file);
        request.setType("articleFile");
        request.setStoreType(STORE_TYPE);
        request.setRelativePath(STORAGE_KEY);
        request.setOriginalName("file.txt");
        return request;
    }

    private void prepareStore(FileVO request, FileVO saved) {
        when(fileSecurityValidator.validateFile(
                request.getFile(),
                "file.txt",
                "text/plain"
        )).thenReturn(FileSecurityValidator.ValidationResult.success("txt"));
        when(fileStorageService.getFileStorage(STORE_TYPE)).thenReturn(storeService);
        when(storeService.getCapability()).thenReturn(new StorageCapability(
                STORE_TYPE,
                true,
                true,
                true,
                true,
                true,
                0,
                List.of()
        ));
        when(storeService.supportsDeterministicWrite()).thenReturn(true);
        if (!Boolean.TRUE.equals(saved.getReuseExistingResource())) {
            when(storeService.getStoreName()).thenReturn(STORE_TYPE);
        }
        when(storeService.resolveAccessPath(STORAGE_KEY)).thenReturn(ACCESS_PATH);
        when(storeService.saveFile(request)).thenReturn(saved);
    }

    private FileVO saved(boolean reused, String hash) {
        FileVO saved = new FileVO();
        saved.setStoreType(STORE_TYPE);
        saved.setStorageKey(STORAGE_KEY);
        saved.setVisitPath(ACCESS_PATH);
        saved.setResourceHash(hash);
        saved.setReuseExistingResource(reused);
        return saved;
    }

    private StorageSnapshot snapshot(String hash, byte[] content) throws Exception {
        Path path = Files.createTempFile(tempDir, "managed-upload-", ".snapshot");
        Files.write(path, content);
        return new StorageSnapshot(path, hash, content.length, "text/plain", "file.txt");
    }

    private void prepareSuccessfulDelete() {
        when(storeService.deleteFiles(any())).thenAnswer(invocation -> {
            List<StorageResourceRef> resources = invocation.getArgument(0);
            return List.of(StorageDeleteResult.deleted(resources.getFirst()));
        });
    }

    private void assertCompensatedTarget(String expectedHash) {
        ArgumentCaptor<List<StorageResourceRef>> resourcesCaptor = ArgumentCaptor.forClass(List.class);
        verify(storeService).deleteFiles(resourcesCaptor.capture());
        assertThat(resourcesCaptor.getValue()).singleElement().satisfies(resource -> {
            assertThat(resource.path()).isEqualTo(ACCESS_PATH);
            assertThat(resource.storageKey()).isEqualTo(STORAGE_KEY);
            assertThat(resource.hash()).isEqualTo(expectedHash);
            assertThat(resource.size()).isEqualTo(CONTENT.length);
        });
    }

    private String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}