package com.ld.poetry.controller.dto;

import java.util.List;

public record ResourceAdoptionRequest(
        List<String> sourceUrls
) {
}