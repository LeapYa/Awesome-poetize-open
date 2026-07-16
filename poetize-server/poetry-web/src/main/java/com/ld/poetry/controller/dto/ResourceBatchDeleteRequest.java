package com.ld.poetry.controller.dto;

import java.util.List;

public record ResourceBatchDeleteRequest(
        List<Target> targets,
        boolean forceReferenced,
        boolean removeMissingRecords,
        boolean removeUnsupportedRecords
) {

    public record Target(Integer resourceId, String expectedPath) {
    }
}