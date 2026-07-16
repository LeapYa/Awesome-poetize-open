package com.ld.poetry.utils.storage;

public record StorageClientAccess(
        String url,
        int maxAgeSeconds,
        boolean privateUrl
) {

    public StorageClientAccess {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("客户端访问地址不能为空");
        }
        if (maxAgeSeconds < 0) {
            throw new IllegalArgumentException("客户端访问地址缓存时间不能小于0");
        }
    }
}