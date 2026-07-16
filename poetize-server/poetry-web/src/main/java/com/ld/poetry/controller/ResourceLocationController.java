package com.ld.poetry.controller;

import com.ld.poetry.aop.AuditLog;
import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.controller.dto.ResourceDetailView;
import com.ld.poetry.controller.dto.ResourceLocationActivateRequest;
import com.ld.poetry.controller.dto.ResourceLocationDeleteRequest;
import com.ld.poetry.controller.dto.ResourceLocationDeleteResult;
import com.ld.poetry.entity.Resource;
import com.ld.poetry.service.ResourceDetailService;
import com.ld.poetry.service.ResourceLocationDeleteService;
import com.ld.poetry.service.ResourceLocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ConcurrentModificationException;

@RestController
@RequestMapping("/resource/location")
@RequiredArgsConstructor
public class ResourceLocationController {

    private final ResourceLocationDeleteService resourceLocationDeleteService;
    private final ResourceDetailService resourceDetailService;
    private final ResourceLocationService resourceLocationService;

    /**
     * 资源详情：聚合逻辑资源、全部物理副本、活动别名与引用计数。
     * 管理端资源详情对话框据此展示稳定 URL、活动存储、副本健康度与 SHA-256 可信状态。
     */
    @GetMapping("/{resourceId}/detail")
    @LoginCheck(0)
    public PoetryResult<ResourceDetailView> detail(@PathVariable Integer resourceId) {
        try {
            return PoetryResult.success(resourceDetailService.loadDetail(resourceId));
        } catch (IllegalArgumentException e) {
            return PoetryResult.fail(e.getMessage());
        }
    }

    @PostMapping("/delete")
    @LoginCheck(0)
    @AuditLog(
            action = "RESOURCE_LOCATION_DELETE",
            targetType = "RESOURCE_LOCATION",
            targetIdParam = "locationId",
            summary = "删除资源物理副本"
    )
    public PoetryResult<ResourceLocationDeleteResult> delete(
            @RequestBody ResourceLocationDeleteRequest request) {
        try {
            ResourceLocationDeleteResult result = resourceLocationDeleteService.delete(request);
            if ("DELETING".equals(result.status())) {
                return PoetryResult.fail(409, result.message(), result);
            }
            if (!result.recordMarkedRemoved()) {
                return PoetryResult.fail(500, result.message(), result);
            }
            return PoetryResult.success(result);
        } catch (ConcurrentModificationException e) {
            // 并发冲突：路径/版本变化、活动副本被迁移/激活、删除声明期间新增引用等
            // 返回 409 让前端提示冲突并刷新副本状态，而非 500 被当作服务端故障
            return PoetryResult.fail(409, e.getMessage(), null);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return PoetryResult.fail(e.getMessage());
        }
    }

    @PostMapping("/{resourceId}/{locationId}/resume-delete")
    @LoginCheck(0)
    @AuditLog(
            action = "RESOURCE_LOCATION_DELETE_RESUME",
            targetType = "RESOURCE_LOCATION",
            targetIdParam = "locationId",
            summary = "恢复资源物理副本删除"
    )
    public PoetryResult<ResourceLocationDeleteResult> resumeDelete(
            @PathVariable Integer resourceId,
            @PathVariable Long locationId) {
        try {
            ResourceLocationDeleteResult result =
                    resourceLocationDeleteService.resumeStale(resourceId, locationId);
            if ("DELETING".equals(result.status())) {
                return PoetryResult.fail(409, result.message(), result);
            }
            if (!result.recordMarkedRemoved()) {
                return PoetryResult.fail(500, result.message(), result);
            }
            return PoetryResult.success(result);
        } catch (ConcurrentModificationException e) {
            return PoetryResult.fail(409, e.getMessage(), null);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return PoetryResult.fail(e.getMessage());
        }
    }

    /**
     * 激活替代副本：把一个已完整回读验证的 RETAINED 副本提升为活动副本。
     * 仅在删除当前活动副本前、或管理员主动切换活动存储时使用。
     * CAS 由 {@code expectedActiveLocationId} 保证，并发冲突返回 409。
     */
    @PostMapping("/{resourceId}/{locationId}/activate")
    @LoginCheck(0)
    @AuditLog(
            action = "RESOURCE_LOCATION_ACTIVATE",
            targetType = "RESOURCE_LOCATION",
            targetIdParam = "locationId",
            summary = "激活替代物理副本为活动副本"
    )
    public PoetryResult<Resource> activate(
            @PathVariable Integer resourceId,
            @PathVariable Long locationId,
            @RequestBody ResourceLocationActivateRequest request) {
        try {
            Resource updated = resourceLocationService.activateReplacementForDeletion(
                    resourceId,
                    request.expectedActiveLocationId(),
                    locationId
            );
            return PoetryResult.success(updated);
        } catch (ConcurrentModificationException e) {
            return PoetryResult.fail(409, e.getMessage(), null);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return PoetryResult.fail(e.getMessage());
        }
    }
}
