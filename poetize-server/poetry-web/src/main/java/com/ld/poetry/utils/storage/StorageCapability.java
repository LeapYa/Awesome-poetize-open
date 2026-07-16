package com.ld.poetry.utils.storage;

import java.util.List;

public record StorageCapability(
        String storeType,
        boolean enabled,
        boolean readSupported,
        boolean uploadSupported,
        boolean deleteSupported,
        boolean verifySupported,
        long maxFileSize,
        List<String> acceptedMimePrefixes
) {

    public boolean supports(String mimeType, long fileSize) {
        if (!enabled || !uploadSupported) {
            return false;
        }
        if (maxFileSize > 0 && fileSize > maxFileSize) {
            return false;
        }
        if (acceptedMimePrefixes == null || acceptedMimePrefixes.isEmpty()) {
            return true;
        }
        String normalizedMimeType = mimeType == null ? "" : mimeType.toLowerCase();
        return acceptedMimePrefixes.stream().anyMatch(normalizedMimeType::startsWith);
    }
}