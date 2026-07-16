package com.ld.poetry.controller.dto;

import com.ld.poetry.entity.ResourceAdoptionItem;
import com.ld.poetry.entity.ResourceAdoptionTask;

import java.util.List;

public record ResourceAdoptionTaskView(
        ResourceAdoptionTask task,
        List<ResourceAdoptionItem> items,
        boolean truncated
) {
}