package com.ld.poetry.service;

import com.ld.poetry.dao.ResourceMigrationItemMapper;
import com.ld.poetry.entity.ResourceMigrationItem;
import com.ld.poetry.enums.ResourceMigrationItemStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ConcurrentModificationException;

@Service
@RequiredArgsConstructor
public class ResourceMigrationSwitchService {

    private final ResourceLocationService resourceLocationService;
    private final ResourceMigrationItemMapper resourceMigrationItemMapper;

    @Transactional(rollbackFor = Exception.class)
    public void switchToTarget(ResourceMigrationItem item) {
        ResourceMigrationItem current = resourceMigrationItemMapper.selectById(item.getId());
        if (current == null || !ResourceMigrationItemStatus.VERIFIED.name().equals(current.getStatus())) {
            throw new ConcurrentModificationException("迁移条目尚未完成目标哈希校验");
        }
        if (current.getSourceLocationId() == null || current.getTargetLocationId() == null) {
            throw new IllegalStateException("迁移条目缺少源或目标物理副本");
        }
        if (!hashEquals(current.getSourceHash(), current.getTargetHash())) {
            throw new IllegalStateException("源快照与目标回读哈希不一致");
        }

        resourceLocationService.activate(
                current.getResourceId(),
                current.getSourceLocationId(),
                current.getSourceLocationVersion(),
                current.getSourceHash(),
                current.getTargetLocationId()
        );

        int updated = resourceMigrationItemMapper.markSwitched(
                current.getId(),
                current.getSourceLocationId(),
                current.getTargetLocationId()
        );
        if (updated != 1) {
            throw new ConcurrentModificationException("迁移条目切换状态已变化");
        }
    }

    private boolean hashEquals(String left, String right) {
        return StringUtils.hasText(left)
                && StringUtils.hasText(right)
                && left.equalsIgnoreCase(right);
    }
}