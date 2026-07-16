package com.ld.poetry.controller;

import com.ld.poetry.aop.AuditLog;
import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.controller.dto.ResourceMigrationCleanupResult;
import com.ld.poetry.controller.dto.ResourceMigrationPreview;
import com.ld.poetry.controller.dto.ResourceMigrationRequest;
import com.ld.poetry.controller.dto.ResourceMigrationTaskView;
import com.ld.poetry.entity.ResourceMigrationTask;
import com.ld.poetry.service.ResourceMigrationService;
import com.ld.poetry.utils.PoetryUtil;
import com.ld.poetry.utils.storage.StorageCapability;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/resource/migration")
@RequiredArgsConstructor
public class ResourceMigrationController {

    private final ResourceMigrationService resourceMigrationService;

    @GetMapping("/capabilities")
    @LoginCheck(0)
    public PoetryResult<List<StorageCapability>> capabilities() {
        return PoetryResult.success(resourceMigrationService.listTargetCapabilities());
    }

    @PostMapping("/preview")
    @LoginCheck(0)
    public PoetryResult<ResourceMigrationPreview> preview(@RequestBody ResourceMigrationRequest request) {
        try {
            return PoetryResult.success(resourceMigrationService.preview(request));
        } catch (IllegalArgumentException e) {
            return PoetryResult.fail(e.getMessage());
        }
    }

    @PostMapping
    @LoginCheck(0)
    @AuditLog(action = "RESOURCE_MIGRATION_CREATE", targetType = "RESOURCE", summary = "创建资源迁移任务")
    public PoetryResult<ResourceMigrationTask> create(@RequestBody ResourceMigrationRequest request) {
        try {
            return PoetryResult.success(resourceMigrationService.create(request, PoetryUtil.getUserId()));
        } catch (IllegalArgumentException e) {
            return PoetryResult.fail(e.getMessage());
        }
    }

    @GetMapping("/{taskId}")
    @LoginCheck(0)
    public PoetryResult<ResourceMigrationTaskView> task(@PathVariable String taskId) {
        try {
            return PoetryResult.success(resourceMigrationService.getTask(taskId));
        } catch (IllegalArgumentException e) {
            return PoetryResult.fail(e.getMessage());
        }
    }

    @PostMapping("/{taskId}/cancel")
    @LoginCheck(0)
    @AuditLog(action = "RESOURCE_MIGRATION_CANCEL", targetType = "RESOURCE", targetIdParam = "taskId", summary = "取消资源迁移任务")
    public PoetryResult<Boolean> cancel(@PathVariable String taskId) {
        try {
            return PoetryResult.success(resourceMigrationService.cancel(taskId));
        } catch (IllegalArgumentException e) {
            return PoetryResult.fail(e.getMessage());
        }
    }

    @PostMapping("/{taskId}/retry")
    @LoginCheck(0)
    @AuditLog(action = "RESOURCE_MIGRATION_RETRY", targetType = "RESOURCE", targetIdParam = "taskId", summary = "重试资源迁移失败项")
    public PoetryResult<ResourceMigrationTask> retry(@PathVariable String taskId) {
        try {
            return PoetryResult.success(resourceMigrationService.retry(taskId));
        } catch (IllegalArgumentException e) {
            return PoetryResult.fail(e.getMessage());
        }
    }

    @PostMapping("/{taskId}/cleanup")
    @LoginCheck(0)
    @AuditLog(action = "RESOURCE_MIGRATION_SOURCE_CLEANUP", targetType = "RESOURCE", targetIdParam = "taskId", summary = "清理资源迁移源文件")
    public PoetryResult<ResourceMigrationCleanupResult> cleanup(@PathVariable String taskId) {
        try {
            return PoetryResult.success(resourceMigrationService.cleanupSources(taskId));
        } catch (IllegalArgumentException e) {
            return PoetryResult.fail(e.getMessage());
        }
    }
}