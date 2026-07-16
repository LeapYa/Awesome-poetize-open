package com.ld.poetry.controller;

import com.ld.poetry.aop.AuditLog;
import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.controller.dto.ResourceAdoptionPreview;
import com.ld.poetry.controller.dto.ResourceAdoptionRequest;
import com.ld.poetry.controller.dto.ResourceAdoptionTaskView;
import com.ld.poetry.entity.ResourceAdoptionTask;
import com.ld.poetry.service.ResourceAdoptionService;
import com.ld.poetry.utils.PoetryUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/resource/adoption")
@RequiredArgsConstructor
public class ResourceAdoptionController {

    private final ResourceAdoptionService adoptionService;

    @PostMapping("/preview")
    @LoginCheck(0)
    public PoetryResult<ResourceAdoptionPreview> preview(
            @RequestBody(required = false) ResourceAdoptionRequest request) {
        try {
            return PoetryResult.success(adoptionService.preview(request));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return PoetryResult.fail(e.getMessage());
        }
    }

    @PostMapping
    @LoginCheck(0)
    @AuditLog(action = "RESOURCE_ADOPTION_CREATE", targetType = "RESOURCE", summary = "创建历史资源接管任务")
    public PoetryResult<ResourceAdoptionTask> create(@RequestBody ResourceAdoptionRequest request) {
        try {
            return PoetryResult.success(adoptionService.create(request, PoetryUtil.getUserIdRequired()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return PoetryResult.fail(e.getMessage());
        }
    }

    @GetMapping("/{taskId}")
    @LoginCheck(0)
    public PoetryResult<ResourceAdoptionTaskView> task(@PathVariable String taskId) {
        try {
            return PoetryResult.success(adoptionService.getTask(taskId));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return PoetryResult.fail(e.getMessage());
        }
    }

    @PostMapping("/{taskId}/cancel")
    @LoginCheck(0)
    @AuditLog(action = "RESOURCE_ADOPTION_CANCEL", targetType = "RESOURCE", targetIdParam = "taskId", summary = "取消历史资源接管任务")
    public PoetryResult<Boolean> cancel(@PathVariable String taskId) {
        try {
            return PoetryResult.success(adoptionService.cancel(taskId));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return PoetryResult.fail(e.getMessage());
        }
    }

    @PostMapping("/{taskId}/retry")
    @LoginCheck(0)
    @AuditLog(action = "RESOURCE_ADOPTION_RETRY", targetType = "RESOURCE", targetIdParam = "taskId", summary = "重试历史资源接管失败项")
    public PoetryResult<ResourceAdoptionTask> retry(@PathVariable String taskId) {
        try {
            return PoetryResult.success(adoptionService.retry(taskId));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return PoetryResult.fail(e.getMessage());
        }
    }
}