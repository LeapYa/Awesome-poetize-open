package com.ld.poetry.service;

import com.ld.poetry.dao.ResourceMapper;
import com.ld.poetry.entity.Resource;
import com.ld.poetry.entity.ResourceLocation;
import com.ld.poetry.enums.ResourceContentState;
import com.ld.poetry.enums.ResourceLocationStatus;
import com.ld.poetry.utils.storage.FileStorageService;
import com.ld.poetry.utils.storage.StorageCapability;
import com.ld.poetry.utils.storage.StorageClientAccess;
import com.ld.poetry.utils.storage.StorageRangeReadHandle;
import com.ld.poetry.utils.storage.StoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceMediaServiceTest {

    private static final String PUBLIC_ID = "0123456789abcdef0123456789abcdef";
    private static final String HASH = "a".repeat(64);

    @Mock
    private ResourceMapper resourceMapper;

    @Mock
    private ResourceLocationService resourceLocationService;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private LocalResourceFileService localResourceFileService;

    @Mock
    private StoreService storeService;

    private ResourceMediaService service;

    @BeforeEach
    void setUp() {
        service = new ResourceMediaService(
                resourceMapper,
                resourceLocationService,
                fileStorageService,
                localResourceFileService
        );
    }

    @Test
    void resolveShouldFreezeVerifiedActiveLocation() {
        Resource resource = resource("qiniu");
        ResourceLocation location = location("qiniu");
        when(resourceMapper.findByPublicId(PUBLIC_ID)).thenReturn(resource);
        when(resourceLocationService.requireActiveLocation(resource)).thenReturn(location);

        ResourceMediaService.MediaDescriptor descriptor = service.resolve(PUBLIC_ID.toUpperCase());

        assertThat(descriptor.resourceId()).isEqualTo(resource.getId());
        assertThat(descriptor.locationId()).isEqualTo(location.getId());
        assertThat(descriptor.locationVersion()).isEqualTo(3);
        assertThat(descriptor.contentHash()).isEqualTo(HASH);
        assertThat(descriptor.size()).isEqualTo(10);
        assertThat(descriptor.etag()).isEqualTo("\"sha256-" + HASH + "\"");
        assertThat(descriptor.storageRef().storageKey()).isEqualTo("assets/image.png");
    }

    @Test
    void pendingReplacementShouldBlockBeforeLocationLookup() {
        Resource resource = resource("local");
        resource.setContentState(ResourceContentState.REPLACEMENT_PENDING.name());
        when(resourceMapper.findByPublicId(PUBLIC_ID)).thenReturn(resource);

        assertThatThrownBy(() -> service.resolve(PUBLIC_ID))
                .isInstanceOf(ResourceMediaAccessException.class)
                .extracting(error -> ((ResourceMediaAccessException) error).reason())
                .isEqualTo(ResourceMediaAccessException.Reason.TEMPORARILY_UNAVAILABLE);

        verify(resourceLocationService, never()).requireActiveLocation(any());
        verify(fileStorageService, never()).getFileStorageByStoreType(any());
    }

    @Test
    void unverifiedLocationHashShouldNeverBecomeStableRepresentation() {
        Resource resource = resource("local");
        ResourceLocation location = location("local");
        location.setContentHash("b".repeat(64));
        when(resourceMapper.findByPublicId(PUBLIC_ID)).thenReturn(resource);
        when(resourceLocationService.requireActiveLocation(resource)).thenReturn(location);

        assertThatThrownBy(() -> service.resolve(PUBLIC_ID))
                .isInstanceOf(ResourceMediaAccessException.class)
                .hasMessageContaining("可信基线");
    }

    @Test
    void remoteClientAccessMustBeRevalidatedByOwningAdapter() {
        Resource resource = resource("qiniu");
        ResourceLocation location = location("qiniu");
        when(resourceMapper.findByPublicId(PUBLIC_ID)).thenReturn(resource);
        when(resourceLocationService.requireActiveLocation(resource)).thenReturn(location);
        when(fileStorageService.getFileStorageByStoreType("qiniu")).thenReturn(storeService);
        when(storeService.getCapability()).thenReturn(capability("qiniu"));
        when(storeService.resolveClientAccess(any())).thenReturn(
                new StorageClientAccess("https://attacker.example/image.png", 60, false)
        );
        when(storeService.isPublicAccessPathTrusted(any())).thenReturn(false);

        ResourceMediaService.MediaDescriptor descriptor = service.resolve(PUBLIC_ID);

        assertThatThrownBy(() -> service.resolveClientAccess(descriptor))
                .isInstanceOf(ResourceMediaAccessException.class)
                .hasMessageContaining("受控客户端访问地址");
    }

    @Test
    void versionChangeAfterOpeningRemoteRangeShouldCloseHandleAndBlockResponse() {
        Resource resource = resource("qiniu");
        Resource changed = resource("qiniu");
        changed.setLocationVersion(4);
        ResourceLocation location = location("qiniu");
        CloseTrackingInputStream inputStream = new CloseTrackingInputStream(new byte[]{1, 2, 3, 4});
        StorageRangeReadHandle handle = StorageRangeReadHandle.bounded(
                inputStream,
                4,
                10,
                "image/png",
                URI.create("https://cdn.example.com/image.png")
        );

        when(resourceMapper.findByPublicId(PUBLIC_ID)).thenReturn(resource, changed);
        when(resourceLocationService.requireActiveLocation(resource)).thenReturn(location);
        when(fileStorageService.getFileStorageByStoreType("qiniu")).thenReturn(storeService);
        when(storeService.getCapability()).thenReturn(capability("qiniu"));
        when(storeService.openReadRange(any(), any(Long.class), any(Long.class))).thenReturn(handle);

        ResourceMediaService.MediaDescriptor descriptor = service.resolve(PUBLIC_ID);

        assertThatThrownBy(() -> service.openRange(descriptor, 2, 5))
                .isInstanceOf(ResourceMediaAccessException.class)
                .hasMessageContaining("状态在响应期间发生变化");
        assertThat(inputStream.closed).isTrue();
    }

    @Test
    void validRemoteRangeShouldReturnHandleAfterSecondStateCheck() throws Exception {
        Resource resource = resource("qiniu");
        ResourceLocation location = location("qiniu");
        StorageRangeReadHandle handle = StorageRangeReadHandle.bounded(
                new ByteArrayInputStream(new byte[]{1, 2, 3, 4}),
                4,
                10,
                "image/png",
                URI.create("https://cdn.example.com/image.png")
        );

        when(resourceMapper.findByPublicId(PUBLIC_ID)).thenReturn(resource, resource);
        when(resourceLocationService.requireActiveLocation(resource)).thenReturn(location);
        when(resourceLocationService.requireLocation(resource.getId(), location.getId())).thenReturn(location);
        when(fileStorageService.getFileStorageByStoreType("qiniu")).thenReturn(storeService);
        when(storeService.getCapability()).thenReturn(capability("qiniu"));
        when(storeService.openReadRange(any(), any(Long.class), any(Long.class))).thenReturn(handle);

        ResourceMediaService.MediaDescriptor descriptor = service.resolve(PUBLIC_ID);

        try (StorageRangeReadHandle opened = service.openRange(descriptor, 2, 5)) {
            assertThat(opened).isSameAs(handle);
            assertThat(opened.inputStream().readAllBytes()).containsExactly(1, 2, 3, 4);
        }
        verify(resourceLocationService).requireLocation(resource.getId(), location.getId());
    }

    private Resource resource(String storeType) {
        Resource resource = new Resource();
        resource.setId(1);
        resource.setPublicId(PUBLIC_ID);
        resource.setPath("/media/" + PUBLIC_ID);
        resource.setStatus(true);
        resource.setStoreType(storeType);
        resource.setStorageKey("assets/image.png");
        resource.setActiveLocationId(21L);
        resource.setLocationVersion(3);
        resource.setContentState(ResourceContentState.ACTIVE.name());
        resource.setResourceHash(HASH);
        resource.setHashVerifiedAt(LocalDateTime.of(2026, 7, 14, 1, 2, 3));
        resource.setOriginalName("image.png");
        resource.setMimeType("image/png");
        resource.setSize(10);
        return resource;
    }

    private ResourceLocation location(String storeType) {
        ResourceLocation location = new ResourceLocation();
        location.setId(21L);
        location.setResourceId(1);
        location.setStoreType(storeType);
        location.setStorageKey("assets/image.png");
        location.setAccessPath("local".equals(storeType)
                ? "/static/assets/image.png"
                : "https://cdn.example.com/image.png");
        location.setContentHash(HASH);
        location.setSize(10L);
        location.setMimeType("image/png");
        location.setStatus(ResourceLocationStatus.ACTIVE.name());
        location.setVerifiedAt(LocalDateTime.of(2026, 7, 14, 1, 2, 3));
        return location;
    }

    private StorageCapability capability(String storeType) {
        return new StorageCapability(
                storeType,
                true,
                true,
                true,
                true,
                true,
                0,
                List.of()
        );
    }

    private static final class CloseTrackingInputStream extends InputStream {
        private final ByteArrayInputStream delegate;
        private boolean closed;

        private CloseTrackingInputStream(byte[] bytes) {
            this.delegate = new ByteArrayInputStream(bytes);
        }

        @Override
        public int read() {
            return delegate.read();
        }

        @Override
        public int read(byte[] bytes, int offset, int length) {
            return delegate.read(bytes, offset, length);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            delegate.close();
        }
    }
}