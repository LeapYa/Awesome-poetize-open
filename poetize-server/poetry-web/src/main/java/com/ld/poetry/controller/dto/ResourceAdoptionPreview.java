package com.ld.poetry.controller.dto;

import com.ld.poetry.service.ResourceAdoptionCandidate;

import java.util.List;

public record ResourceAdoptionPreview(
        int candidateCount,
        int trustedCount,
        int untrustedCount,
        int referenceCount,
        List<ResourceAdoptionCandidate> candidates
) {
}