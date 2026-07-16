package com.ld.poetry.controller.dto;

import com.ld.poetry.entity.ResourceMigrationItem;
import com.ld.poetry.entity.ResourceMigrationTask;

import java.util.List;

public record ResourceMigrationTaskView(
        ResourceMigrationTask task,
        List<ResourceMigrationItem> items,
        boolean itemsTruncated
) {
}