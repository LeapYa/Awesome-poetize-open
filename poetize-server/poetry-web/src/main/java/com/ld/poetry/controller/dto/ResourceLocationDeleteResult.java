package com.ld.poetry.controller.dto;

public record ResourceLocationDeleteResult(
        Integer resourceId,
        Long locationId,
        Long activeLocationId,
        String status,
        boolean physicalDeleted,
        boolean recordMarkedRemoved,
        String message
) {
}