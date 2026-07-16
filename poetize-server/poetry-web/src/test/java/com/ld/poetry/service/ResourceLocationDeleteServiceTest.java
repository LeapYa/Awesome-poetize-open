package com.ld.poetry.service;

import com.ld.poetry.controller.dto.ResourceLocationDeleteRequest;
import com.ld.poetry.controller.dto.ResourceLocationDeleteResult;
import com.ld.poetry.entity.Resource;
import com.ld.poetry.entity.ResourceLocation;
import com.ld.poetry.enums.ResourceLocationStatus;
import com.ld.poetry.utils.storage.FileStorageService;
import com.ld.poetry.utils.storage.StorageCapability;
import com.ld.poetry.utils.storage.StorageDeleteResult;
import com.ld.poetry.utils.storage.StorageResourceRef;
import com.ld.poetry.utils.storage.StorageSnapshot;
import com.ld.poetry.utils.storage.StorageVerificationResult;
import com.ld.poetry.utils.storage.StoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ConcurrentModificationException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceLocationDeleteServiceTest {

    @Mock
    private ResourceLocationService resourceLocationService;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private StoreService storeService;

    @Mock
    private ResourceStorageSnapshotService snapshotService;

    @Mock
    private StorageSnapshot snapshot;

    private ResourceLocationDeleteService service;

    @BeforeEach
    void setUp() {
        service = new ResourceLocationDeleteService(
                resourceLocationService,
                fileStorageService,
                snapshotService
        );
    }

    @Test
    void deleteRetainedLocationShouldCompleteClaim() {
        Resource resource = resource(1, 20L);
        ResourceLocation location = location(10L, ResourceLocationStatus.RETAINED);
        LocalDateTime claimedAt = LocalDateTime.of(2026, 7, 14, 12, 0);
        ResourceLocationService.LocationDeletionClaim claim = claim(resource, location, claimedAt);
        when(resourceLocationService.requireLocation(1, 10L)).thenReturn(location);
        when(fileStorageService.getFileStorageByStoreType("qiniu")).thenReturn(storeService);
        when(storeService.getCapability()).thenReturn(capability(true));
        when(resourceLocationService.claimLocationDeletion(1, 10L, null)).thenReturn(claim);
        when(storeService.deleteFiles(any())).thenAnswer(invocation -> {
            List<StorageResourceRef> refs = invocation.getArgument(0);
            return List.of(StorageDeleteResult.deleted(refs.getFirst()));
        });

        ResourceLocationDeleteResult result = service.delete(new ResourceLocationDeleteRequest(1, 10L, null));

        assertThat(result.status()).isEqualTo(ResourceLocationStatus.DELETED.name());
        assertThat(result.activeLocationId()).isEqualTo(20L);
        assertThat(result.physicalDeleted()).isTrue();
        assertThat(result.recordMarkedRemoved()).isTrue();
        verify(resourceLocationService).completeLocationDeletion(1, 10L, claimedAt, false);
        verify(resourceLocationService, never()).restoreLocationDeletion(any(), any(), any(), any());
    }

    @Test
    void failedPhysicalDeleteShouldRestoreRetainedState() {
        Resource resource = resource(1, 20L);
        ResourceLocation location = location(10L, ResourceLocationStatus.RETAINED);
        LocalDateTime claimedAt = LocalDateTime.of(2026, 7, 14, 12, 0);
        when(resourceLocationService.requireLocation(1, 10L)).thenReturn(location);
        when(fileStorageService.getFileStorageByStoreType("qiniu")).thenReturn(storeService);
        when(storeService.getCapability()).thenReturn(capability(true));
        when(resourceLocationService.claimLocationDeletion(1, 10L, null))
                .thenReturn(claim(resource, location, claimedAt));
        when(storeService.deleteFiles(any())).thenAnswer(invocation -> {
            List<StorageResourceRef> refs = invocation.getArgument(0);
            return List.of(StorageDeleteResult.failed(refs.getFirst(), "remote timeout"));
        });
        when(storeService.verify(any())).thenReturn(StorageVerificationResult.unknown("timeout"));
        when(snapshotService.capture(any(), any())).thenReturn(snapshot);
        when(snapshot.sha256()).thenReturn("a".repeat(64));
        when(snapshot.size()).thenReturn(128L);

        ResourceLocationDeleteResult result = service.delete(new ResourceLocationDeleteRequest(1, 10L, null));

        assertThat(result.status()).isEqualTo(ResourceLocationStatus.RETAINED.name());
        assertThat(result.physicalDeleted()).isFalse();
        assertThat(result.recordMarkedRemoved()).isFalse();
        verify(resourceLocationService).restoreLocationDeletion(
                1, 10L, claimedAt, ResourceLocationStatus.RETAINED.name()
        );
        verify(resourceLocationService, never()).completeLocationDeletion(any(), any(), any(), any(boolean.class));
    }

    @Test
    void deletingLocationShouldNotIssueSecondPhysicalDelete() {
        Resource resource = resource(1, 20L);
        ResourceLocation location = location(10L, ResourceLocationStatus.DELETING);
        when(resourceLocationService.requireLocation(1, 10L)).thenReturn(location);
        when(fileStorageService.getFileStorageByStoreType("qiniu")).thenReturn(storeService);
        when(storeService.getCapability()).thenReturn(capability(true));
        when(resourceLocationService.claimLocationDeletion(1, 10L, null))
                .thenReturn(new ResourceLocationService.LocationDeletionClaim(
                        resource,
                        location,
                        false,
                        ResourceLocationStatus.DELETING.name(),
                        null
                ));

        ResourceLocationDeleteResult result = service.delete(new ResourceLocationDeleteRequest(1, 10L, null));

        assertThat(result.status()).isEqualTo(ResourceLocationStatus.DELETING.name());
        assertThat(result.recordMarkedRemoved()).isFalse();
        verify(storeService, never()).deleteFiles(any());
    }

    @Test
    void activeLocationWithoutReplacementShouldBeRejectedBeforePhysicalDelete() {
        ResourceLocation location = location(10L, ResourceLocationStatus.ACTIVE);
        when(resourceLocationService.requireLocation(1, 10L)).thenReturn(location);
        when(fileStorageService.getFileStorageByStoreType("qiniu")).thenReturn(storeService);
        when(storeService.getCapability()).thenReturn(capability(true));
        when(resourceLocationService.claimLocationDeletion(1, 10L, null))
                .thenThrow(new IllegalArgumentException("删除活动副本前必须指定不同的替代副本"));

        assertThatThrownBy(() -> service.delete(new ResourceLocationDeleteRequest(1, 10L, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("替代副本");

        verify(storeService, never()).deleteFiles(any());
    }

    @Test
    void unsupportedStoreShouldNotClaimLocation() {
        ResourceLocation location = location(10L, ResourceLocationStatus.RETAINED);
        when(resourceLocationService.requireLocation(1, 10L)).thenReturn(location);
        when(fileStorageService.getFileStorageByStoreType("qiniu")).thenReturn(storeService);
        when(storeService.getCapability()).thenReturn(capability(false));

        assertThatThrownBy(() -> service.delete(new ResourceLocationDeleteRequest(1, 10L, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不支持");

        verify(resourceLocationService).requireLocation(1, 10L);
        verify(resourceLocationService, never()).claimLocationDeletion(any(), any(), any());
    }

    @Test
    void uncertainDeleteWithFailingReadbackShouldKeepDeleting() {
        Resource resource = resource(1, 20L);
        ResourceLocation location = location(10L, ResourceLocationStatus.RETAINED);
        LocalDateTime claimedAt = LocalDateTime.of(2026, 7, 14, 12, 0);
        when(resourceLocationService.requireLocation(1, 10L)).thenReturn(location);
        when(fileStorageService.getFileStorageByStoreType("qiniu")).thenReturn(storeService);
        when(storeService.getCapability()).thenReturn(capability(true));
        when(resourceLocationService.claimLocationDeletion(1, 10L, null))
                .thenReturn(claim(resource, location, claimedAt));
        when(storeService.deleteFiles(any())).thenAnswer(invocation -> {
            List<StorageResourceRef> refs = invocation.getArgument(0);
            return List.of(StorageDeleteResult.failed(refs.getFirst(), "remote timeout"));
        });
        when(storeService.verify(any())).thenReturn(StorageVerificationResult.unknown("timeout"));
        // capture 不声明 checked exception；用 RuntimeException 模拟回读失败，
        // 被 canProveOriginalContent 的 catch (IOException | RuntimeException) 捕获，返回 false，保持 DELETING
        when(snapshotService.capture(any(), any())).thenThrow(new RuntimeException("回读失败"));

        ResourceLocationDeleteResult result = service.delete(new ResourceLocationDeleteRequest(1, 10L, null));

        assertThat(result.status()).isEqualTo(ResourceLocationStatus.DELETING.name());
        assertThat(result.physicalDeleted()).isFalse();
        assertThat(result.recordMarkedRemoved()).isFalse();
        verify(resourceLocationService, never()).restoreLocationDeletion(any(), any(), any(), any());
        verify(resourceLocationService, never()).completeLocationDeletion(any(), any(), any(), any(boolean.class));
    }

    @Test
    void missingVerificationAfterFailedDeleteShouldMarkMissing() {
        Resource resource = resource(1, 20L);
        ResourceLocation location = location(10L, ResourceLocationStatus.RETAINED);
        LocalDateTime claimedAt = LocalDateTime.of(2026, 7, 14, 12, 0);
        when(resourceLocationService.requireLocation(1, 10L)).thenReturn(location);
        when(fileStorageService.getFileStorageByStoreType("qiniu")).thenReturn(storeService);
        when(storeService.getCapability()).thenReturn(capability(true));
        when(resourceLocationService.claimLocationDeletion(1, 10L, null))
                .thenReturn(claim(resource, location, claimedAt));
        when(storeService.deleteFiles(any())).thenAnswer(invocation -> {
            List<StorageResourceRef> refs = invocation.getArgument(0);
            return List.of(StorageDeleteResult.failed(refs.getFirst(), "remote error"));
        });
        when(storeService.verify(any())).thenReturn(StorageVerificationResult.missing("对象不存在"));

        ResourceLocationDeleteResult result = service.delete(new ResourceLocationDeleteRequest(1, 10L, null));

        assertThat(result.status()).isEqualTo(ResourceLocationStatus.MISSING.name());
        assertThat(result.recordMarkedRemoved()).isTrue();
        verify(resourceLocationService).completeLocationDeletion(1, 10L, claimedAt, true);
        verify(resourceLocationService, never()).restoreLocationDeletion(any(), any(), any(), any());
    }

    @Test
    void resumeStaleShouldNotClaimActiveLease() {
        Resource resource = resource(1, 20L);
        ResourceLocation location = location(10L, ResourceLocationStatus.DELETING);
        when(resourceLocationService.requireLocation(1, 10L)).thenReturn(location);
        when(fileStorageService.getFileStorageByStoreType("qiniu")).thenReturn(storeService);
        when(storeService.getCapability()).thenReturn(capability(true));
        when(resourceLocationService.reclaimStaleLocationDeletion(eq(1), eq(10L), any()))
                .thenReturn(new ResourceLocationService.LocationDeletionClaim(
                        resource, location, false, ResourceLocationStatus.DELETING.name(), null
                ));

        ResourceLocationDeleteResult result = service.resumeStale(1, 10L);

        assertThat(result.status()).isEqualTo(ResourceLocationStatus.DELETING.name());
        assertThat(result.recordMarkedRemoved()).isFalse();
        verify(storeService, never()).deleteFiles(any());
    }

    @Test
    void resumeStaleShouldContinueAfterLeaseExpires() {
        Resource resource = resource(1, 20L);
        ResourceLocation location = location(10L, ResourceLocationStatus.DELETING);
        LocalDateTime claimedAt = LocalDateTime.of(2026, 7, 14, 12, 30);
        when(resourceLocationService.requireLocation(1, 10L)).thenReturn(location);
        when(fileStorageService.getFileStorageByStoreType("qiniu")).thenReturn(storeService);
        when(storeService.getCapability()).thenReturn(capability(true));
        when(resourceLocationService.reclaimStaleLocationDeletion(eq(1), eq(10L), any()))
                .thenReturn(claim(resource, location, claimedAt));
        when(storeService.deleteFiles(any())).thenAnswer(invocation -> {
            List<StorageResourceRef> refs = invocation.getArgument(0);
            return List.of(StorageDeleteResult.deleted(refs.getFirst()));
        });

        ResourceLocationDeleteResult result = service.resumeStale(1, 10L);

        assertThat(result.status()).isEqualTo(ResourceLocationStatus.DELETED.name());
        assertThat(result.physicalDeleted()).isTrue();
        verify(resourceLocationService).completeLocationDeletion(1, 10L, claimedAt, false);
    }

    @Test
    void staleLeaseShouldNotCompleteWithNewLease() {
        Resource resource = resource(1, 20L);
        ResourceLocation location = location(10L, ResourceLocationStatus.RETAINED);
        LocalDateTime claimedAt = LocalDateTime.of(2026, 7, 14, 12, 0);
        when(resourceLocationService.requireLocation(1, 10L)).thenReturn(location);
        when(fileStorageService.getFileStorageByStoreType("qiniu")).thenReturn(storeService);
        when(storeService.getCapability()).thenReturn(capability(true));
        when(resourceLocationService.claimLocationDeletion(1, 10L, null))
                .thenReturn(claim(resource, location, claimedAt));
        when(storeService.deleteFiles(any())).thenAnswer(invocation -> {
            List<StorageResourceRef> refs = invocation.getArgument(0);
            return List.of(StorageDeleteResult.deleted(refs.getFirst()));
        });
        doThrow(new ConcurrentModificationException("物理副本删除收尾状态或租约已变化"))
                .when(resourceLocationService)
                .completeLocationDeletion(1, 10L, claimedAt, false);

        assertThatThrownBy(() -> service.delete(new ResourceLocationDeleteRequest(1, 10L, null)))
                .isInstanceOf(ConcurrentModificationException.class)
                .hasMessageContaining("租约已变化");
    }

    private ResourceLocationService.LocationDeletionClaim claim(Resource resource,
                                                                 ResourceLocation location,
                                                                 LocalDateTime claimedAt) {
        return new ResourceLocationService.LocationDeletionClaim(
                resource,
                location,
                true,
                ResourceLocationStatus.RETAINED.name(),
                claimedAt
        );
    }

    private Resource resource(Integer id, Long activeLocationId) {
        Resource resource = new Resource();
        resource.setId(id);
        resource.setActiveLocationId(activeLocationId);
        resource.setOriginalName("image.png");
        return resource;
    }

    private ResourceLocation location(Long id, ResourceLocationStatus status) {
        ResourceLocation location = new ResourceLocation();
        location.setId(id);
        location.setResourceId(1);
        location.setStoreType("qiniu");
        location.setStorageKey("assets/image.png");
        location.setAccessPath("https://cdn.example.com/assets/image.png");
        location.setContentHash("a".repeat(64));
        location.setSize(128L);
        location.setMimeType("image/png");
        location.setStatus(status.name());
        return location;
    }

    private StorageCapability capability(boolean deleteSupported) {
        return new StorageCapability(
                "qiniu",
                true,
                true,
                true,
                deleteSupported,
                true,
                0,
                List.of()
        );
    }
}