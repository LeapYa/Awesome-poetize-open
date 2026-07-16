package com.ld.poetry.service;

import com.ld.poetry.dao.ResourceMigrationItemMapper;
import com.ld.poetry.entity.ResourceMigrationItem;
import com.ld.poetry.enums.ResourceMigrationItemStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceMigrationSwitchServiceTest {

    private static final String CONTENT_HASH = "a".repeat(64);

    @Mock
    private ResourceLocationService resourceLocationService;

    @Mock
    private ResourceMigrationItemMapper itemMapper;

    private ResourceMigrationSwitchService service;

    @BeforeEach
    void setUp() {
        service = new ResourceMigrationSwitchService(resourceLocationService, itemMapper);
    }

    @Test
    void matchingVerifiedItemShouldActivateTargetAndMarkSwitched() {
        ResourceMigrationItem item = item(ResourceMigrationItemStatus.VERIFIED, CONTENT_HASH, CONTENT_HASH);
        when(itemMapper.selectById(item.getId())).thenReturn(item);
        when(itemMapper.markSwitched(
                item.getId(), item.getSourceLocationId(), item.getTargetLocationId()
        )).thenReturn(1);

        service.switchToTarget(item);

        verify(resourceLocationService).activate(
                item.getResourceId(),
                item.getSourceLocationId(),
                item.getSourceLocationVersion(),
                item.getSourceHash(),
                item.getTargetLocationId()
        );
        verify(itemMapper).markSwitched(
                item.getId(), item.getSourceLocationId(), item.getTargetLocationId()
        );
    }

    @Test
    void hashMismatchShouldNeverActivateTarget() {
        ResourceMigrationItem item = item(
                ResourceMigrationItemStatus.VERIFIED,
                CONTENT_HASH,
                "b".repeat(64)
        );
        when(itemMapper.selectById(item.getId())).thenReturn(item);

        assertThatThrownBy(() -> service.switchToTarget(item))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("哈希不一致");

        verify(resourceLocationService, never()).activate(any(), any(), any(), any(), any());
        verify(itemMapper, never()).markSwitched(any(), any(), any());
    }

    @Test
    void nonVerifiedItemShouldNeverActivateTarget() {
        ResourceMigrationItem item = item(
                ResourceMigrationItemStatus.TARGET_WRITTEN,
                CONTENT_HASH,
                CONTENT_HASH
        );
        when(itemMapper.selectById(item.getId())).thenReturn(item);

        assertThatThrownBy(() -> service.switchToTarget(item))
                .isInstanceOf(java.util.ConcurrentModificationException.class)
                .hasMessageContaining("尚未完成目标哈希校验");

        verify(resourceLocationService, never()).activate(any(), any(), any(), any(), any());
        verify(itemMapper, never()).markSwitched(any(), any(), any());
    }

    private ResourceMigrationItem item(ResourceMigrationItemStatus status,
                                       String sourceHash,
                                       String targetHash) {
        ResourceMigrationItem item = new ResourceMigrationItem();
        item.setId(11L);
        item.setResourceId(1);
        item.setSourceLocationId(21L);
        item.setSourceLocationVersion(3);
        item.setTargetLocationId(22L);
        item.setSourceHash(sourceHash);
        item.setTargetHash(targetHash);
        item.setStatus(status.name());
        return item;
    }
}