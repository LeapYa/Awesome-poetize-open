package com.ld.poetry.utils.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class StorageSnapshot implements AutoCloseable {

    private final Path path;
    private final String sha256;
    private final long size;
    private final String contentType;
    private final String originalName;

    public StorageSnapshot(Path path,
                           String sha256,
                           long size,
                           String contentType,
                           String originalName) {
        this.path = path;
        this.sha256 = sha256;
        this.size = size;
        this.contentType = contentType;
        this.originalName = originalName;
    }

    public Path path() {
        return path;
    }

    public String sha256() {
        return sha256;
    }

    public long size() {
        return size;
    }

    public String contentType() {
        return contentType;
    }

    public String originalName() {
        return originalName;
    }

    @Override
    public void close() throws IOException {
        Files.deleteIfExists(path);
    }
}