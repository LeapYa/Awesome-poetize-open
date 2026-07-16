package com.ld.poetry.utils.storage;

public record StorageResourceRef(
        Integer resourceId,
        String path,
        String storageKey,
        String originalName,
        Long size,
        String hash,
        String mimeType
) {

    public static StorageResourceRef pathOnly(String path) {
        return new StorageResourceRef(null, path, null, null, null, null, null);
    }
}