package com.ld.poetry.service;

public class ResourceMediaAccessException extends RuntimeException {

    public enum Reason {
        NOT_FOUND,
        TEMPORARILY_UNAVAILABLE
    }

    private final Reason reason;

    public ResourceMediaAccessException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public ResourceMediaAccessException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}