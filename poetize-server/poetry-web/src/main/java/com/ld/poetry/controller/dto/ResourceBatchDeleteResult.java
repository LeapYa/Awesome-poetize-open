package com.ld.poetry.controller.dto;

import java.util.List;

public record ResourceBatchDeleteResult(
        int requestedCount,
        int readyCount,
        int deletedCount,
        int blockedCount,
        int failedCount,
        List<Item> items
) {

    public record Item(
            Integer resourceId,
            String path,
            String storeType,
            String status,
            int referenceCount,
            boolean deleteSupported,
            String verificationState,
            boolean physicalDeleted,
            boolean recordDeleted,
            String message
    ) {
    }
}