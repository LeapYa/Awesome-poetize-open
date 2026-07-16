package com.ld.poetry.controller.dto;

import com.ld.poetry.utils.storage.StorageCapability;

import java.util.List;

public record ResourceMigrationPreview(
        StorageCapability targetCapability,
        int selectedCount,
        int eligibleCount,
        int skippedCount,
        long eligibleBytes,
        List<Item> items,
        boolean itemsTruncated
) {

    public record Item(
            Integer resourceId,
            String path,
            String originalName,
            String mimeType,
            Long size,
            boolean eligible,
            String reason
    ) {
    }
}