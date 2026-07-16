package com.ld.poetry.enums;

public enum ResourceReplacementStatus {
    PENDING,
    RECOVERY_REQUIRED,
    COMMITTED,
    ABORTED;

    public boolean isOpen() {
        return this == PENDING || this == RECOVERY_REQUIRED;
    }
}