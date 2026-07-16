package com.ld.poetry.controller.dto;

import java.util.List;

public record ResourceMigrationRequest(
        List<Target> targets,
        String scopeType,
        String resourceType,
        String targetStoreType,
        Boolean keepSource
) {

    public record Target(Integer resourceId, String expectedPath) {
    }
}