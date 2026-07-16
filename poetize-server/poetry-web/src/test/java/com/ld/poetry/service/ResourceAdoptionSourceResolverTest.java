package com.ld.poetry.service;

import com.ld.poetry.utils.mail.MailUtil;
import com.ld.poetry.utils.storage.FileStorageService;
import com.ld.poetry.utils.storage.StorageCapability;
import com.ld.poetry.utils.storage.StoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceAdoptionSourceResolverTest {

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private MailUtil mailUtil;

    @Mock
    private StoreService storeService;

    private ResourceAdoptionSourceResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ResourceAdoptionSourceResolver(fileStorageService, mailUtil);
        ReflectionTestUtils.setField(resolver, "localDownloadUrl", "/static/");
    }

    @Test
    void protocolRelativeUrlUsesTrustedRemoteStoreInsteadOfLocalStorage() {
        String sourceUrl = "//cdn.example.com/images/cover.png";
        String readUrl = "https://cdn.example.com/images/cover.png";
        when(fileStorageService.listFileStorages()).thenReturn(List.of(storeService));
        when(storeService.getCapability()).thenReturn(new StorageCapability(
                "qiniu", true, true, true, true, true, 0, List.of()
        ));
        when(storeService.isPublicAccessPathTrusted(readUrl)).thenReturn(true);
        when(storeService.resolveStorageKey(readUrl)).thenReturn("images/cover.png");
        when(storeService.getStoreName()).thenReturn("qiniu");

        ResourceAdoptionSourceResolver.ResolvedSource resolved = resolver.resolve(sourceUrl);

        assertThat(resolved.sourceUrl()).isEqualTo(sourceUrl);
        assertThat(resolved.storeType()).isEqualTo("qiniu");
        assertThat(resolved.storageKey()).isEqualTo("images/cover.png");
        assertThat(resolved.accessPath()).isEqualTo(readUrl);
        assertThat(resolved.resourceRef().path()).isEqualTo(readUrl);
    }

    @Test
    void localUrlWithQueryIsRejectedBecauseBytesCannotBeProvenEquivalent() {
        assertThatThrownBy(() -> resolver.resolve("/static/images/cover.png?width=320"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("无法证明");

        verify(fileStorageService, never()).getFileStorageByStoreType("local");
    }

    @Test
    void unknownRemoteDomainIsRejected() {
        when(fileStorageService.listFileStorages()).thenReturn(List.of(storeService));
        when(storeService.getCapability()).thenReturn(new StorageCapability(
                "qiniu", true, true, true, true, true, 0, List.of()
        ));

        assertThatThrownBy(() -> resolver.resolve("https://untrusted.example.com/cover.png"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("可信图床域名");
    }
}