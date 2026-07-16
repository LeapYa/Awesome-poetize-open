package com.ld.poetry.utils.storage;

public record StorageVerificationResult(
        State state,
        Long size,
        String hash,
        String message
) {

    public enum State {
        AVAILABLE,
        MISSING,
        UNKNOWN
    }

    public static StorageVerificationResult available(Long size, String hash) {
        return new StorageVerificationResult(State.AVAILABLE, size, hash, "");
    }

    public static StorageVerificationResult missing(String message) {
        return new StorageVerificationResult(State.MISSING, null, null, message == null ? "文件不存在" : message);
    }

    public static StorageVerificationResult unknown(String message) {
        return new StorageVerificationResult(State.UNKNOWN, null, null, message == null ? "无法校验" : message);
    }

    public boolean isAvailable() {
        return state == State.AVAILABLE;
    }
}