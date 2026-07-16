package com.ld.poetry.utils.storage;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

public final class StorageRangeReadHandle implements AutoCloseable {

    private final InputStream inputStream;
    private final long contentLength;
    private final long totalLength;
    private final String contentType;
    private final URI resolvedUri;

    private StorageRangeReadHandle(InputStream inputStream,
                                   long contentLength,
                                   long totalLength,
                                   String contentType,
                                   URI resolvedUri) {
        if (inputStream == null) {
            throw new IllegalArgumentException("区间读取流不能为空");
        }
        if (contentLength <= 0 || totalLength <= 0 || contentLength > totalLength) {
            closeQuietly(inputStream);
            throw new IllegalArgumentException("区间读取长度不合法");
        }
        this.inputStream = new LengthLimitedInputStream(inputStream, contentLength);
        this.contentLength = contentLength;
        this.totalLength = totalLength;
        this.contentType = contentType;
        this.resolvedUri = resolvedUri;
    }

    public static StorageRangeReadHandle bounded(InputStream inputStream,
                                                 long contentLength,
                                                 long totalLength,
                                                 String contentType,
                                                 URI resolvedUri) {
        return new StorageRangeReadHandle(
                inputStream,
                contentLength,
                totalLength,
                contentType,
                resolvedUri
        );
    }

    public InputStream inputStream() {
        return inputStream;
    }

    public long contentLength() {
        return contentLength;
    }

    public long totalLength() {
        return totalLength;
    }

    public String contentType() {
        return contentType;
    }

    public URI resolvedUri() {
        return resolvedUri;
    }

    @Override
    public void close() throws IOException {
        inputStream.close();
    }

    private static void closeQuietly(InputStream inputStream) {
        try {
            inputStream.close();
        } catch (IOException ignored) {
            // 构造失败时尽力释放底层资源。
        }
    }

    private static final class LengthLimitedInputStream extends FilterInputStream {
        private long remaining;

        private LengthLimitedInputStream(InputStream inputStream, long length) {
            super(inputStream);
            this.remaining = length;
        }

        @Override
        public int read() throws IOException {
            if (remaining == 0) {
                return -1;
            }
            int value = super.read();
            if (value >= 0) {
                remaining--;
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            if (remaining == 0) {
                return -1;
            }
            int allowed = (int) Math.min(length, remaining);
            int read = super.read(bytes, offset, allowed);
            if (read > 0) {
                remaining -= read;
            }
            return read;
        }
    }
}