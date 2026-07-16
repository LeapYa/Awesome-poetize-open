package com.ld.poetry.service;

import com.ld.poetry.entity.Resource;
import com.ld.poetry.entity.ResourceLocation;

public record ResourceMigrationCandidate(
        Resource resource,
        ResourceLocation sourceLocation,
        boolean eligible,
        String reason
) {

    public ResourceMigrationCandidate(Resource resource, boolean eligible, String reason) {
        this(resource, null, eligible, reason);
    }
}