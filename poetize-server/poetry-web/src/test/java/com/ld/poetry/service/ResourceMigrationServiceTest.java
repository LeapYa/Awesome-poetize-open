package com.ld.poetry.service;

import com.ld.poetry.controller.dto.ResourceLocationDeleteResult;
import com.ld.poetry.controller.dto.ResourceMigrationCleanupResult;
import com.ld.poetry.controller.dto.ResourceMigrationRequest;
import com.ld.poetry.dao.ResourceMigrationItemMapper;
import com.ld.poetry.entity.Resource;
import com.ld.poetry.entity.ResourceLocation;
import com.ld.poetry.entity.ResourceMigrationItem;
import com.ld.poetry.entity.ResourceMigrationTask;
import com.ld.poetry.enums.ResourceLocationStatus;
import com.ld.poetry.enums.ResourceMigrationItemStatus;
import com.ld.poetry.enums.ResourceMigrationTaskStatus;
import com.ld.poetry.utils.storage.FileStorageService;
import com.ld.poetry.utils.storage.StorageCapability;
import com.ld.poetry.utils.storage.StorageResourceRef;
import com.ld.poetry.utils.storage.StorageSnapshot;
import com.ld.poetry.utils.storage.StorageVerificationResult;
import com.ld.poetry.utils.storage.StoreService;
import com.ld.poetry.vo.FileVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceMigrationServiceTest {

    private static final String SOURCE_HASH = "a".repeat(64);
    private static final String OTHER_HASH = "b".repeat(64);

    @Mock
    private ResourceMigrationCandidateService candidateService;

    @Mock
    private ResourceMigrationTaskStore taskStore;

    @Mock
    private ResourceMigrationItemMapper itemMapper;

    @Mock
    private ResourceService resourceService;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private ResourceStorageSnapshotService snapshotService;

    @Mock
    private ResourceLocationService resourceLocationService;

    @Mock
    private ResourceLocationDeleteService resourceLocationDeleteService;

    @Mock
    private ResourceMigrationSwitchService switchService;

    @Mock
    private ResourceMigrationCacheService cacheService;

    @Mock
    private StoreService targetService;

    @Mock
    private StoreService sourceService;

    @TempDir
    private Path tempDir;

    private ResourceMigrationService service;

    @BeforeEach
    void setUp() {
        service = new ResourceMigrationService(
                candidateService,
                taskStore,
                itemMapper,
                resourceService,
                fileStorageService,
                snapshotService,
                resourceLocationService,
                resourceLocationDeleteService,
                switchService,
                cacheService
        );
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    @Test
    void createShouldRejectDisablingSourceRetention() {
        ResourceMigrationRequest request = request(false);

        assertThatThrownBy(() -> service.create(request, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须保留源文件");

        verifyNoInteractions(candidateService, taskStore);
    }

    @Test
    void createShouldRejectRangeWithoutStrictlyEligibleResources() {
        Resource resource = resource(1);
        ResourceMigrationRequest request = request(true);
        when(candidateService.resolveCandidates(request)).thenReturn(List.of(
                new ResourceMigrationCandidate(resource, false, "目标存储不支持完整回读")
        ));

        assertThatThrownBy(() -> service.create(request, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("没有符合严格迁移条件的资源");

        verify(taskStore, never()).createTask(any(), any(), any());
    }

    @Test
    void verifiedItemShouldSwitchBeforeBecomingSuccessful() {
        ResourceMigrationItem verified = item(ResourceMigrationItemStatus.VERIFIED);
        ResourceMigrationItem switched = copyWithStatus(verified, ResourceMigrationItemStatus.SWITCHED);
        when(itemMapper.selectById(verified.getId())).thenReturn(verified, switched);

        Boolean changed = ReflectionTestUtils.invokeMethod(service, "processItem", verified);

        assertThat(changed).isTrue();
        verify(switchService).switchToTarget(verified);
        verify(taskStore).markSuccess(switched, false);
    }

    @Test
    void committedSwitchShouldInvalidateCacheEvenWhenSuccessFinalizationFails() {
        ResourceMigrationTask pending = task(ResourceMigrationTaskStatus.PENDING);
        ResourceMigrationTask running = task(ResourceMigrationTaskStatus.RUNNING);
        ResourceMigrationItem verified = item(ResourceMigrationItemStatus.VERIFIED);
        ResourceMigrationItem switched = copyWithStatus(verified, ResourceMigrationItemStatus.SWITCHED);
        when(taskStore.findTask(pending.getTaskId())).thenReturn(pending, running);
        when(taskStore.markRunning(pending)).thenReturn(true);
        when(itemMapper.selectList(any())).thenReturn(List.of(verified));
        when(itemMapper.selectById(verified.getId())).thenReturn(verified, switched, switched);
        doThrow(new IllegalStateException("迁移成功状态收尾失败"))
                .when(taskStore).markSuccess(switched, false);

        ReflectionTestUtils.invokeMethod(service, "runTask", pending.getTaskId());

        verify(switchService).switchToTarget(verified);
        verify(taskStore).markFailed(switched, "迁移成功状态收尾失败");
        verify(cacheService).invalidateAfterMigration();
    }

    @Test
    void runtimeTargetCapabilityDowngradeShouldFailBeforeWriting() throws Exception {
        ResourceMigrationItem snapshotReady = item(ResourceMigrationItemStatus.SNAPSHOT_READY);
        Resource resource = resource(snapshotReady.getResourceId());
        when(resourceService.getById(snapshotReady.getResourceId())).thenReturn(resource);
        when(fileStorageService.getFileStorageByStoreType(snapshotReady.getTargetStoreType()))
                .thenReturn(targetService);
        when(targetService.getCapability()).thenReturn(new StorageCapability(
                snapshotReady.getTargetStoreType(), true, true, true, true, false, 0, List.of()
        ));

        try (StorageSnapshot sourceSnapshot = snapshot(SOURCE_HASH, 128)) {
            assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                    service,
                    "writeTarget",
                    snapshotReady,
                    resource,
                    sourceSnapshot
            ))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("不再满足严格迁移能力要求");
        }

        verify(taskStore, never()).markWriting(any(), any(), any());
        verify(targetService, never()).saveFile(any());
    }

    @Test
    void targetReadBackHashMismatchShouldNeverSwitch() throws Exception {
        ResourceMigrationItem written = item(ResourceMigrationItemStatus.TARGET_WRITTEN);
        ResourceMigrationItem verifying = copyWithStatus(written, ResourceMigrationItemStatus.VERIFYING);
        Resource resource = resource(written.getResourceId());
        when(itemMapper.selectById(written.getId())).thenReturn(written, verifying, verifying);
        when(resourceService.getById(written.getResourceId())).thenReturn(resource);
        when(fileStorageService.getFileStorageByStoreType(written.getTargetStoreType())).thenReturn(targetService);
        when(targetService.verify(any())).thenReturn(StorageVerificationResult.available(128L, null));
        when(taskStore.markVerifying(written)).thenReturn(true);
        when(snapshotService.capture(eq(targetService), any())).thenReturn(snapshot(OTHER_HASH, 128));

        Boolean changed = ReflectionTestUtils.invokeMethod(service, "processItem", written);

        assertThat(changed).isFalse();
        verify(taskStore).markFailed(
                verifying,
                "目标完整回读哈希不一致：源=" + SOURCE_HASH + "，目标=" + OTHER_HASH
        );
        verifyNoInteractions(switchService);
        verify(resourceLocationService, never()).stageLocation(
                anyInt(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void concurrentSourceVersionChangeShouldMarkSourceChanged() throws Exception {
        ResourceMigrationItem written = item(ResourceMigrationItemStatus.TARGET_WRITTEN);
        ResourceMigrationItem verifying = copyWithStatus(written, ResourceMigrationItemStatus.VERIFYING);
        Resource stable = resource(written.getResourceId());
        Resource changed = resource(written.getResourceId());
        changed.setLocationVersion(stable.getLocationVersion() + 1);
        when(itemMapper.selectById(written.getId())).thenReturn(written, verifying, verifying);
        when(resourceService.getById(written.getResourceId())).thenReturn(stable, changed);
        when(fileStorageService.getFileStorageByStoreType(written.getTargetStoreType())).thenReturn(targetService);
        when(targetService.verify(any())).thenReturn(StorageVerificationResult.available(128L, null));
        when(taskStore.markVerifying(written)).thenReturn(true);
        when(snapshotService.capture(eq(targetService), any())).thenReturn(snapshot(SOURCE_HASH, 128));

        Boolean changedLocation = ReflectionTestUtils.invokeMethod(service, "processItem", written);

        assertThat(changedLocation).isFalse();
        verify(taskStore).markSourceChanged(verifying, "资源活动副本在迁移期间发生变化");
        verifyNoInteractions(switchService);
        verify(resourceLocationService, never()).stageLocation(
                anyInt(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void missingTargetShouldReturnToSourceSnapshotInsteadOfSwitching() {
        ResourceMigrationItem written = item(ResourceMigrationItemStatus.TARGET_WRITTEN);
        Resource resource = resource(written.getResourceId());
        when(resourceService.getById(written.getResourceId())).thenReturn(resource);
        when(fileStorageService.getFileStorageByStoreType(written.getTargetStoreType())).thenReturn(targetService);
        when(targetService.verify(any())).thenReturn(StorageVerificationResult.missing("目标对象不存在"));

        ReflectionTestUtils.invokeMethod(service, "verifyTarget", written);

        verify(taskStore).resetMissingTarget(written);
        verify(taskStore, never()).markVerifying(any());
        verifyNoInteractions(snapshotService, switchService);
    }

    @Test
    void cleanupShouldKeepSourceWhenActiveTargetCannotBeFullyRead() {
        ResourceMigrationTask task = task(ResourceMigrationTaskStatus.SUCCESS);
        ResourceMigrationItem item = item(ResourceMigrationItemStatus.SUCCESS);
        Resource resource = resource(item.getResourceId());
        resource.setActiveLocationId(item.getTargetLocationId());
        ResourceLocation targetLocation = location(
                item.getTargetLocationId(),
                item.getResourceId(),
                item.getTargetStoreType(),
                ResourceLocationStatus.ACTIVE
        );
        when(taskStore.findTask(task.getTaskId())).thenReturn(task);
        when(itemMapper.selectList(any())).thenReturn(List.of(item));
        when(resourceService.getById(item.getResourceId())).thenReturn(resource);
        when(resourceLocationService.requireLocation(item.getResourceId(), item.getTargetLocationId()))
                .thenReturn(targetLocation);
        when(fileStorageService.getFileStorageByStoreType(targetLocation.getStoreType())).thenReturn(targetService);
        when(snapshotService.capture(eq(targetService), any()))
                .thenThrow(new IllegalStateException("目标暂时不可读"));

        ResourceMigrationCleanupResult result = service.cleanupSources(task.getTaskId());

        assertThat(result.candidateCount()).isEqualTo(1);
        assertThat(result.cleanedCount()).isZero();
        assertThat(result.skippedCount()).isZero();
        assertThat(result.failedCount()).isEqualTo(1);
        verify(taskStore, never()).markSourceDeleted(any());
        verify(sourceService, never()).deleteFiles(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void cleanupShouldDeleteFrozenRemoteSourceAfterTargetHashReverification() throws Exception {
        ResourceMigrationTask task = task(ResourceMigrationTaskStatus.SUCCESS);
        ResourceMigrationItem item = item(ResourceMigrationItemStatus.SUCCESS);
        item.setSourceStoreType("qiniu");
        item.setTargetStoreType("local");
        Resource resource = resource(item.getResourceId());
        resource.setStoreType("local");
        resource.setActiveLocationId(item.getTargetLocationId());
        ResourceLocation targetLocation = location(
                item.getTargetLocationId(),
                item.getResourceId(),
                "local",
                ResourceLocationStatus.ACTIVE
        );
        ResourceLocation sourceLocation = location(
                item.getSourceLocationId(),
                item.getResourceId(),
                "qiniu",
                ResourceLocationStatus.RETAINED
        );
        when(taskStore.findTask(task.getTaskId())).thenReturn(task);
        when(itemMapper.selectList(any())).thenReturn(List.of(item));
        when(resourceService.getById(item.getResourceId())).thenReturn(resource);
        when(resourceLocationService.requireLocation(item.getResourceId(), item.getTargetLocationId()))
                .thenReturn(targetLocation);
        when(resourceLocationService.requireLocation(item.getResourceId(), item.getSourceLocationId()))
                .thenReturn(sourceLocation);
        when(fileStorageService.getFileStorageByStoreType("local")).thenReturn(targetService);
        when(snapshotService.capture(eq(targetService), any())).thenReturn(snapshot(SOURCE_HASH, 128));
        when(resourceLocationDeleteService.delete(any())).thenReturn(
                new ResourceLocationDeleteResult(
                        item.getResourceId(),
                        item.getSourceLocationId(),
                        item.getTargetLocationId(),
                        ResourceLocationStatus.DELETED.name(),
                        true,
                        true,
                        "物理副本已删除"
                )
        );

        ResourceMigrationCleanupResult result = service.cleanupSources(task.getTaskId());

        assertThat(result.cleanedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isZero();
        assertThat(result.failedCount()).isZero();
        verify(resourceLocationDeleteService).delete(any());
        verify(taskStore).markSourceDeleted(item);
    }

    @Test
    void cleanupShouldRejectCancelledTask() {
        ResourceMigrationTask task = task(ResourceMigrationTaskStatus.CANCELLED);
        when(taskStore.findTask(task.getTaskId())).thenReturn(task);

        assertThatThrownBy(() -> service.cleanupSources(task.getTaskId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("任务完成后才能清理源文件");

        verifyNoInteractions(itemMapper, fileStorageService, snapshotService);
    }

    @Test
    void receiptBasedWriteShouldUseMarkUploadingAndMarkUploadedForNonDeterministicTarget() throws Exception {
        ResourceMigrationItem snapshotReady = item(ResourceMigrationItemStatus.SNAPSHOT_READY);
        ResourceMigrationItem uploading = copyWithStatus(snapshotReady, ResourceMigrationItemStatus.UPLOADING);
        Resource resource = resource(snapshotReady.getResourceId());
        when(resourceService.getById(snapshotReady.getResourceId())).thenReturn(resource);
        when(fileStorageService.getFileStorageByStoreType(snapshotReady.getTargetStoreType()))
                .thenReturn(targetService);
        when(targetService.getCapability()).thenReturn(new StorageCapability(
                snapshotReady.getTargetStoreType(), true, true, true, true, true, 0, List.of("image/")
        ));
        when(targetService.supportsDeterministicWrite()).thenReturn(false);
        when(taskStore.markUploading(snapshotReady)).thenReturn(true);
        when(itemMapper.selectById(snapshotReady.getId())).thenReturn(uploading);

        FileVO result = new FileVO();
        result.setVisitPath("https://lsky.example.com/i/2024/abc.png");
        result.setStorageKey("lsky-del-token-abc");
        when(targetService.saveFile(any())).thenReturn(result);

        try (StorageSnapshot sourceSnapshot = snapshot(SOURCE_HASH, 128)) {
            ReflectionTestUtils.invokeMethod(
                    service,
                    "writeTarget",
                    snapshotReady,
                    resource,
                    sourceSnapshot
            );
        }

        verify(taskStore).markUploading(snapshotReady);
        verify(targetService).saveFile(any());
        verify(taskStore).markUploaded(uploading, "https://lsky.example.com/i/2024/abc.png", "lsky-del-token-abc");
        verify(taskStore, never()).markWriting(any(), any(), any());
        verify(taskStore, never()).markTargetWritten(any(), any(), any(), anyBoolean());
    }

    @Test
    void receiptBasedWriteShouldFailWhenTargetStorageKeyMissing() throws Exception {
        ResourceMigrationItem snapshotReady = item(ResourceMigrationItemStatus.SNAPSHOT_READY);
        Resource resource = resource(snapshotReady.getResourceId());
        when(resourceService.getById(snapshotReady.getResourceId())).thenReturn(resource);
        when(fileStorageService.getFileStorageByStoreType(snapshotReady.getTargetStoreType()))
                .thenReturn(targetService);
        when(targetService.getCapability()).thenReturn(new StorageCapability(
                snapshotReady.getTargetStoreType(), true, true, true, true, true, 0, List.of("image/")
        ));
        when(targetService.supportsDeterministicWrite()).thenReturn(false);
        when(taskStore.markUploading(snapshotReady)).thenReturn(true);

        FileVO result = new FileVO();
        result.setVisitPath("https://lsky.example.com/i/2024/abc.png");
        result.setStorageKey(null);
        when(targetService.saveFile(any())).thenReturn(result);

        try (StorageSnapshot sourceSnapshot = snapshot(SOURCE_HASH, 128)) {
            assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                    service,
                    "writeTarget",
                    snapshotReady,
                    resource,
                    sourceSnapshot
            ))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("未返回有效的物理地址或对象键");
        }

        verify(taskStore, never()).markUploaded(any(), any(), any());
    }

    private ResourceMigrationRequest request(Boolean keepSource) {
        return new ResourceMigrationRequest(
                List.of(new ResourceMigrationRequest.Target(1, "/media/0123456789abcdef0123456789abcdef")),
                "SELECTED",
                "assets",
                "qiniu",
                keepSource
        );
    }

    private ResourceMigrationTask task(ResourceMigrationTaskStatus status) {
        ResourceMigrationTask task = new ResourceMigrationTask();
        task.setId(10L);
        task.setTaskId("task-1");
        task.setTargetStoreType("qiniu");
        task.setStatus(status.name());
        task.setTotalCount(1);
        task.setProcessedCount(status.isTerminal() ? 1 : 0);
        task.setSuccessCount(status == ResourceMigrationTaskStatus.SUCCESS ? 1 : 0);
        task.setSkippedCount(0);
        task.setFailedCount(0);
        return task;
    }

    private ResourceMigrationItem item(ResourceMigrationItemStatus status) {
        ResourceMigrationItem item = new ResourceMigrationItem();
        item.setId(11L);
        item.setTaskId("task-1");
        item.setResourceId(1);
        item.setSourceLocationId(21L);
        item.setSourceLocationVersion(3);
        item.setSourcePath("https://source.example.com/image.png");
        item.setSourceStoreType("qiniu");
        item.setSourceStorageKey("source/image.png");
        item.setSourceExpectedHash(SOURCE_HASH);
        item.setSourceHash(SOURCE_HASH);
        item.setSourceSize(128L);
        item.setSourceMimeType("image/png");
        item.setTargetLocationId(22L);
        item.setTargetPath("https://target.example.com/resources/public/hash");
        item.setTargetStoreType("qiniu");
        item.setTargetStorageKey("resources/public/hash");
        item.setTargetHash(SOURCE_HASH);
        item.setSnapshotSize(128L);
        item.setTargetCreated(true);
        item.setStatus(status.name());
        item.setRetryCount(0);
        item.setSourceDeleted(false);
        return item;
    }

    private ResourceMigrationItem copyWithStatus(ResourceMigrationItem source,
                                                  ResourceMigrationItemStatus status) {
        ResourceMigrationItem copy = item(status);
        copy.setId(source.getId());
        return copy;
    }

    private Resource resource(Integer id) {
        Resource resource = new Resource();
        resource.setId(id);
        resource.setPublicId("0123456789abcdef0123456789abcdef");
        resource.setPath("/media/0123456789abcdef0123456789abcdef");
        resource.setStoreType("qiniu");
        resource.setStorageKey("source/image.png");
        resource.setActiveLocationId(21L);
        resource.setLocationVersion(3);
        resource.setOriginalName("image.png");
        resource.setMimeType("image/png");
        resource.setSize(128);
        resource.setResourceHash(SOURCE_HASH);
        return resource;
    }

    private ResourceLocation location(Long id,
                                      Integer resourceId,
                                      String storeType,
                                      ResourceLocationStatus status) {
        ResourceLocation location = new ResourceLocation();
        location.setId(id);
        location.setResourceId(resourceId);
        location.setStoreType(storeType);
        location.setStorageKey(storeType + "/image.png");
        location.setAccessPath("https://" + storeType + ".example.com/image.png");
        location.setContentHash(SOURCE_HASH);
        location.setSize(128L);
        location.setMimeType("image/png");
        location.setStatus(status.name());
        return location;
    }

    private StorageSnapshot snapshot(String hash, long size) throws Exception {
        Path path = Files.createTempFile(tempDir, "migration-test-", ".bin");
        Files.write(path, new byte[]{1});
        return new StorageSnapshot(path, hash, size, "image/png", "image.png");
    }
}