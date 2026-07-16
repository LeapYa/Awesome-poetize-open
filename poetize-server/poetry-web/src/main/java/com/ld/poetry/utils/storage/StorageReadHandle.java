package com.ld.poetry.utils.storage;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

public final class StorageReadHandle implements AutoCloseable {

    private final InputStream inputStream;
    private final Long contentLength;
    private final String contentType;
    private final URI resolvedUri;

    private StorageReadHandle(InputStream inputStream,
                              Long contentLength,
                              String contentType,
                              URI resolvedUri,
                              long maxBytes) {
        if (inputStream == null) {
            throw new IllegalArgumentException("存储读取流不能为空");
        }
        if (maxBytes <= 0) {
            try {
                inputStream.close();
            } catch (IOException ignored) {
                // 构造失败时尽力释放底层连接。
            }
            throw new IllegalArgumentException("存储读取上限必须大于0");
        }
        if (contentLength != null && contentLength > maxBytes) {
            try {
                inputStream.close();
            } catch (IOException ignored) {
                // 构造失败时尽力释放底层连接。
            }
            throw new IllegalStateException("资源大小超过迁移读取上限");
        }
        this.inputStream = new BoundedInputStream(inputStream, maxBytes);
        this.contentLength = contentLength;
        this.contentType = contentType;
        this.resolvedUri = resolvedUri;
    }

    public static StorageReadHandle bounded(InputStream inputStream,
                                            Long contentLength,
                                            String contentType,
                                            URI resolvedUri,
                                            long maxBytes) {
        return new StorageReadHandle(inputStream, contentLength, contentType, resolvedUri, maxBytes);
    }

    public InputStream inputStream() {
        return inputStream;
    }

    public Long contentLength() {
        return contentLength;
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

    private static final class BoundedInputStream extends FilterInputStream {
        private final long maxBytes;
        private long count;

        private BoundedInputStream(InputStream inputStream, long maxBytes) {
            super(inputStream);
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
                record(1);
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int read = super.read(bytes, offset, length);
            if (read > 0) {
                record(read);
            }
            return read;
        }

        private void record(int bytes) throws IOException {
            count += bytes;
            if (count > maxBytes) {
                throw new IOException("资源内容超过迁移读取上限");
            }
        }
    }
}