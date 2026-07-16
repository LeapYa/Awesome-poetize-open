package com.ld.poetry.service;

import com.ld.poetry.controller.dto.ResourceBatchDeleteRequest;
import com.ld.poetry.controller.dto.ResourceBatchDeleteResult;
import com.ld.poetry.controller.dto.ResourceLocationDeleteResult;
import com.ld.poetry.entity.Resource;
import com.ld.poetry.entity.ResourceLocation;
import com.ld.poetry.enums.ResourceContentState;
import com.ld.poetry.enums.ResourceLocationStatus;
import com.ld.poetry.utils.storage.FileStorageService;
import com.ld.poetry.utils.storage.StorageCapability;
import com.ld.poetry.utils.storage.StorageVerificationResult;
import com.ld.poetry.utils.storage.StoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceBatchDeleteServiceTest {

    @Mock
    private ResourceDeletionStateService deletionStateService;

    @Mock
    private ResourceLocationDeleteService locationDeleteService;

    @Mock
    private ResourceReferenceService referenceService;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private StoreService localStore;

    @Mock
    private StoreService qiniuStore;

    private ResourceBatchDeleteService service;

    @BeforeEach
    void setUp() {
        service = new ResourceBatchDeleteService(
                deletionStateService,
                locationDeleteService,
                referenceService,
                fileStorageService
        );
    }

    @Test
    void deleteShouldBlockStableOrAliasReferencesByDefault() {
        Resource resource = resource(1);
        when(deletionStateService.requireResource(1)).thenReturn(resource);
        when(deletionStateService.listActiveAliases(1)).thenReturn(List.of("/static/old-cover.png"));
        when(referenceService.countReferences(resource.getPath())).thenReturn(2);
        when(referenceService.countReferences("/static/old-cover.png")).thenReturn(1);

        ResourceBatchDeleteResult result = service.delete(request(resource, false, false, false));

        assertThat(result.deletedCount()).isZero();
        assertThat(result.blockedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isZero();
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.status()).isEqualTo("REFERENCED");
            assertThat(item.referenceCount()).isEqualTo(3);
            assertThat(item.recordDeleted()).isFalse();
            assertThat(item.physicalDeleted()).isFalse();
        });
        verify(deletionStateService, never()).listLocations(any());
        verifyNoInteractions(locationDeleteService, fileStorageService);
    }

    @Test
    void deleteShouldKeepMissingReplicaWithoutExplicitAuthorization() {
        Resource resource = resource(2);
        ResourceLocation location = location(21L, "local", ResourceLocationStatus.ACTIVE);
        stubUnreferenced(resource);
        when(deletionStateService.listLocations(2)).thenReturn(List.of(location));
        when(fileStorageService.getFileStorageByStoreType("local")).thenReturn(localStore);
        when(localStore.getCapability()).thenReturn(capability("local", true));
        when(localStore.verify(any())).thenReturn(StorageVerificationResult.missing("本地文件不存在"));

        ResourceBatchDeleteResult result = service.delete(request(resource, false, false, false));

        assertThat(result.deletedCount()).isZero();
        assertThat(result.blockedCount()).isEqualTo(1);
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.status()).isEqualTo("MISSING");
            assertThat(item.recordDeleted()).isFalse();
        });
        verify(deletionStateService, never()).claim(any());
        verifyNoInteractions(locationDeleteService);
    }

    @Test
    void deleteShouldKeepUnsupportedReplicaWithoutExplicitDetachment() {
        Resource resource = resource(3);
        ResourceLocation location = location(31L, "easyimage", ResourceLocationStatus.ACTIVE);
        stubUnreferenced(resource);
        when(deletionStateService.listLocations(3)).thenReturn(List.of(location));
        when(fileStorageService.getFileStorageByStoreType("easyimage")).thenReturn(qiniuStore);
        when(qiniuStore.getCapability()).thenReturn(capability("easyimage", false));

        ResourceBatchDeleteResult result = service.delete(request(resource, false, false, false));

        assertThat(result.deletedCount()).isZero();
        assertThat(result.blockedCount()).isEqualTo(1);
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.status()).isEqualTo("UNSUPPORTED");
            assertThat(item.deleteSupported()).isFalse();
            assertThat(item.recordDeleted()).isFalse();
        });
        verify(qiniuStore, never()).verify(any());
        verify(deletionStateService, never()).claim(any());
    }

    @Test
    void deleteShouldClaimAndFinalizeEveryReplica() {
        Resource resource = resource(4);
        ResourceLocation active = location(41L, "local", ResourceLocationStatus.ACTIVE);
        ResourceLocation retained = location(42L, "qiniu", ResourceLocationStatus.RETAINED);
        stubUnreferenced(resource);
        when(deletionStateService.listLocations(4)).thenReturn(List.of(active, retained));
        when(fileStorageService.getFileStorageByStoreType("local")).thenReturn(localStore);
        when(fileStorageService.getFileStorageByStoreType("qiniu")).thenReturn(qiniuStore);
        when(localStore.getCapability()).thenReturn(capability("local", true));
        when(qiniuStore.getCapability()).thenReturn(capability("qiniu", true));
        when(localStore.verify(any())).thenReturn(StorageVerificationResult.available(128L, null));
        when(qiniuStore.verify(any())).thenReturn(StorageVerificationResult.available(128L, null));

        LocalDateTime claimedAt = LocalDateTime.of(2026, 7, 14, 12, 0);
        ResourceLocationService.LocationDeletionClaim activeClaim =
                claim(resource, active, claimedAt, ResourceLocationStatus.ACTIVE);
        ResourceLocationService.LocationDeletionClaim retainedClaim =
                claim(resource, retained, claimedAt.plusSeconds(1), ResourceLocationStatus.RETAINED);
        when(deletionStateService.claim(any())).thenReturn(
                new ResourceDeletionStateService.LogicalDeletionClaim(
                        resource,
                        List.of(activeClaim, retainedClaim),
                        List.of()
                )
        );
        when(locationDeleteService.deleteClaimed(activeClaim)).thenReturn(
                deleted(resource, active)
        );
        when(locationDeleteService.deleteClaimed(retainedClaim)).thenReturn(
                deleted(resource, retained)
        );
        when(deletionStateService.finalizeDeletion(4, resource.getPath(), false)).thenReturn(true);

        ResourceBatchDeleteResult result = service.delete(request(resource, false, false, false));

        assertThat(result.deletedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isZero();
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.status()).isEqualTo("DELETED");
            assertThat(item.recordDeleted()).isTrue();
            assertThat(item.physicalDeleted()).isTrue();
            assertThat(item.storeType()).isEqualTo("MIXED");
        });
        verify(locationDeleteService).deleteClaimed(activeClaim);
        verify(locationDeleteService).deleteClaimed(retainedClaim);
        verify(deletionStateService).finalizeDeletion(4, resource.getPath(), false);

        ArgumentCaptor<ResourceDeletionStateService.LogicalDeletionPlan> planCaptor =
                ArgumentCaptor.forClass(ResourceDeletionStateService.LogicalDeletionPlan.class);
        verify(deletionStateService).claim(planCaptor.capture());
        assertThat(planCaptor.getValue().dispositions())
                .extracting(ResourceDeletionStateService.LocationDeletionDisposition::locationId)
                .containsExactlyInAnyOrder(41L, 42L);
        assertThat(planCaptor.getValue().forceReferenced()).isFalse();
    }

    @Test
    void explicitFlagsShouldMarkMissingAndDetachUnsupportedReplicas() {
        Resource resource = resource(5);
        ResourceLocation missing = location(51L, "local", ResourceLocationStatus.ACTIVE);
        ResourceLocation unsupported = location(52L, "easyimage", ResourceLocationStatus.RETAINED);
        stubUnreferenced(resource);
        when(deletionStateService.listLocations(5)).thenReturn(List.of(missing, unsupported));
        when(fileStorageService.getFileStorageByStoreType("local")).thenReturn(localStore);
        when(fileStorageService.getFileStorageByStoreType("easyimage")).thenReturn(qiniuStore);
        when(localStore.getCapability()).thenReturn(capability("local", true));
        when(qiniuStore.getCapability()).thenReturn(capability("easyimage", false));
        when(localStore.verify(any())).thenReturn(StorageVerificationResult.missing("missing"));
        when(deletionStateService.claim(any())).thenReturn(
                new ResourceDeletionStateService.LogicalDeletionClaim(resource, List.of(), List.of())
        );
        when(deletionStateService.finalizeDeletion(5, resource.getPath(), false)).thenReturn(true);

        ResourceBatchDeleteResult result = service.delete(request(resource, false, true, true));

        assertThat(result.deletedCount()).isEqualTo(1);
        ArgumentCaptor<ResourceDeletionStateService.LogicalDeletionPlan> planCaptor =
                ArgumentCaptor.forClass(ResourceDeletionStateService.LogicalDeletionPlan.class);
        verify(deletionStateService).claim(planCaptor.capture());
        assertThat(planCaptor.getValue().dispositions()).containsExactlyInAnyOrder(
                new ResourceDeletionStateService.LocationDeletionDisposition(
                        51L,
                        ResourceDeletionStateService.LocationDisposition.MARK_MISSING
                ),
                new ResourceDeletionStateService.LocationDeletionDisposition(
                        52L,
                        ResourceDeletionStateService.LocationDisposition.DETACH
                )
        );
        verifyNoInteractions(locationDeleteService);
    }

    private void stubUnreferenced(Resource resource) {
        when(deletionStateService.requireResource(resource.getId())).thenReturn(resource);
        when(deletionStateService.listActiveAliases(resource.getId())).thenReturn(List.of());
        when(referenceService.countReferences(resource.getPath())).thenReturn(0);
    }

    private ResourceLocationService.LocationDeletionClaim claim(
            Resource resource,
            ResourceLocation location,
            LocalDateTime claimedAt,
            ResourceLocationStatus originalStatus) {
        location.setStatus(ResourceLocationStatus.DELETING.name());
        return new ResourceLocationService.LocationDeletionClaim(
                resource,
                location,
                true,
                originalStatus.name(),
                claimedAt
        );
    }

    private ResourceLocationDeleteResult deleted(Resource resource, ResourceLocation location) {
        return new ResourceLocationDeleteResult(
                resource.getId(),
                location.getId(),
                resource.getActiveLocationId(),
                ResourceLocationStatus.DELETED.name(),
                true,
                true,
                "物理副本已删除"
        );
    }

    private ResourceBatchDeleteRequest request(Resource resource,
                                               boolean forceReferenced,
                                               boolean removeMissingRecords,
                                               boolean removeUnsupportedRecords) {
        return new ResourceBatchDeleteRequest(
                List.of(new ResourceBatchDeleteRequest.Target(resource.getId(), resource.getPath())),
                forceReferenced,
                removeMissingRecords,
                removeUnsupportedRecords
        );
    }

    private StorageCapability capability(String storeType, boolean deleteSupported) {
        return new StorageCapability(
                storeType,
                true,
                true,
                true,
                deleteSupported,
                true,
                0,
                List.of()
        );
    }

    private Resource resource(Integer id) {
        Resource resource = new Resource();
        resource.setId(id);
        resource.setPath("/media/public-" + id);
        resource.setStoreType("local");
        resource.setOriginalName("resource.png");
        resource.setMimeType("image/png");
        resource.setResourceHash("a".repeat(64));
        resource.setSize(128);
        resource.setStatus(true);
        resource.setContentState(ResourceContentState.ACTIVE.name());
        resource.setLocationVersion(0);
        resource.setActiveLocationId(id.longValue() * 10 + 1);
        return resource;
    }

    private ResourceLocation location(Long id,
                                      String storeType,
                                      ResourceLocationStatus status) {
        ResourceLocation location = new ResourceLocation();
        location.setId(id);
        location.setResourceId(Math.toIntExact(id / 10));
        location.setStoreType(storeType);
        location.setStorageKey("assets/" + id + ".png");
        location.setAccessPath("/physical/" + id + ".png");
        location.setContentHash("a".repeat(64));
        location.setSize(128L);
        location.setMimeType("image/png");
        location.setStatus(status.name());
        return location;
    }
}