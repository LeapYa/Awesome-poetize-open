package com.ld.poetry.enums;

import org.springframework.util.StringUtils;

public enum ResourceContentState {
    ACTIVE,
    REPLACEMENT_PENDING,
    DELETION_PENDING;

    public static boolean isActive(String value) {
        return !StringUtils.hasText(value) || ACTIVE.name().equals(value);
    }
}