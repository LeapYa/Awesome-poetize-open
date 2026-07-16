package com.ld.poetry.controller.dto;

public record ResourceLocationDeleteRequest(
        Integer resourceId,
        Long locationId,
        Long replacementLocationId
) {
}