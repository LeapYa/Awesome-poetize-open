package com.ld.poetry.controller.dto;

public record ResourceMigrationCleanupResult(
        int candidateCount,
        int cleanedCount,
        int skippedCount,
        int failedCount
) {
}