package com.ld.poetry.service;

public record ResourceAdoptionCandidate(
        String sourceUrl,
        int referenceCount,
        String classification,
        boolean trusted,
        String storeType,
        String reason
) {
}