package com.ld.poetry.enums;

public enum ResourceAdoptionItemStatus {
    PENDING,
    READING,
    ADOPTED,
    SKIPPED,
    FAILED;

    public boolean isTerminal() {
        return this == ADOPTED || this == SKIPPED || this == FAILED;
    }
}