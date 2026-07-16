package com.ld.poetry.utils.storage;

public record StorageDeleteResult(
        StorageResourceRef resource,
        boolean success,
        boolean missing,
        String message
) {

    public static StorageDeleteResult deleted(StorageResourceRef resource) {
        return new StorageDeleteResult(resource, true, false, "");
    }

    public static StorageDeleteResult missing(StorageResourceRef resource) {
        return new StorageDeleteResult(resource, false, true, "物理文件不存在");
    }

    public static StorageDeleteResult failed(StorageResourceRef resource, String message) {
        return new StorageDeleteResult(resource, false, false, message == null ? "删除失败" : message);
    }
}