package com.ld.poetry.enums;

public enum ResourceMigrationTaskStatus {
    PENDING,
    RUNNING,
    PARTIAL_SUCCESS,
    SUCCESS,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == PARTIAL_SUCCESS || this == SUCCESS || this == FAILED || this == CANCELLED;
    }
}