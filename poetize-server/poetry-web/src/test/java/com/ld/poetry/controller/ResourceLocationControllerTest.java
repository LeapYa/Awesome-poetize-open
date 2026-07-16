package com.ld.poetry.controller;

import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.controller.dto.ResourceLocationDeleteRequest;
import com.ld.poetry.controller.dto.ResourceLocationDeleteResult;
import com.ld.poetry.service.ResourceLocationDeleteService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ConcurrentModificationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 验证 {@link ResourceLocationController} 的 HTTP 状态映射：
 * <ul>
 *   <li>并发冲突（{@link ConcurrentModificationException}）必须返回 409，而非被当作 500 服务端故障。</li>
 *   <li>删除服务返回 {@code DELETING} 表示删除未决（租约持有中或回读不确定），同样返回 409。</li>
 *   <li>{@code recordMarkedRemoved=false} 的非 DELETING 结果表示物理删除失败且记录未清理，返回 500。</li>
 *   <li>非法参数/状态异常返回默认 500 fail。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ResourceLocationControllerTest {

    @Mock
    private ResourceLocationDeleteService resourceLocationDeleteService;

    @InjectMocks
    private ResourceLocationController controller;

    @Test
    void deleteShouldReturn409OnConcurrentModificationException() {
        ResourceLocationDeleteRequest request = new ResourceLocationDeleteRequest(1, 10L, 20L);
        when(resourceLocationDeleteService.delete(request))
                .thenThrow(new ConcurrentModificationException("副本版本已变化"));

        PoetryResult<ResourceLocationDeleteResult> result = controller.delete(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(409);
        assertThat(result.getMessage()).contains("副本版本已变化");
    }

    @Test
    void deleteShouldReturn409WhenServiceReturnsDeletingStatus() {
        ResourceLocationDeleteRequest request = new ResourceLocationDeleteRequest(1, 10L, 20L);
        ResourceLocationDeleteResult serviceResult = new ResourceLocationDeleteResult(
                1, 10L, 20L, "DELETING", false, false, "删除未决，等待回读确认");
        when(resourceLocationDeleteService.delete(request)).thenReturn(serviceResult);

        PoetryResult<ResourceLocationDeleteResult> result = controller.delete(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(409);
        assertThat(result.getMessage()).contains("删除未决");
        // DELETING 场景仍把结果作为 data 返回，便于前端展示租约/副本状态
        assertThat(result.getData()).isSameAs(serviceResult);
    }

    @Test
    void deleteShouldReturn500WhenPhysicalDeleteFailsAndRecordNotRemoved() {
        ResourceLocationDeleteRequest request = new ResourceLocationDeleteRequest(1, 10L, 20L);
        // 非 DELETING 且 recordMarkedRemoved=false：物理删除失败，记录残留
        ResourceLocationDeleteResult serviceResult = new ResourceLocationDeleteResult(
                1, 10L, 20L, "RETAINED", false, false, "回读校验失败，已恢复原状态");
        when(resourceLocationDeleteService.delete(request)).thenReturn(serviceResult);

        PoetryResult<ResourceLocationDeleteResult> result = controller.delete(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMessage()).contains("回读校验失败");
    }

    @Test
    void deleteShouldReturnSuccessWhenRecordMarkedRemoved() {
        ResourceLocationDeleteRequest request = new ResourceLocationDeleteRequest(1, 10L, 20L);
        ResourceLocationDeleteResult serviceResult = new ResourceLocationDeleteResult(
                1, 10L, 20L, "DELETED", true, true, "删除完成");
        when(resourceLocationDeleteService.delete(request)).thenReturn(serviceResult);

        PoetryResult<ResourceLocationDeleteResult> result = controller.delete(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isSameAs(serviceResult);
    }

    @Test
    void deleteShouldReturnDefaultFailOnIllegalArgumentException() {
        ResourceLocationDeleteRequest request = new ResourceLocationDeleteRequest(1, 10L, null);
        when(resourceLocationDeleteService.delete(request))
                .thenThrow(new IllegalArgumentException("replacementLocationId 不能为空"));

        PoetryResult<ResourceLocationDeleteResult> result = controller.delete(request);

        assertThat(result.isSuccess()).isFalse();
        // 默认 fail(String) 返回 500
        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMessage()).contains("replacementLocationId");
    }

    @Test
    void deleteShouldReturnDefaultFailOnIllegalStateException() {
        ResourceLocationDeleteRequest request = new ResourceLocationDeleteRequest(1, 10L, 20L);
        when(resourceLocationDeleteService.delete(request))
                .thenThrow(new IllegalStateException("已有物理副本记录不可重新作为活动源使用"));

        PoetryResult<ResourceLocationDeleteResult> result = controller.delete(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMessage()).contains("不可重新作为活动源使用");
    }

    @Test
    void resumeDeleteShouldReturn409OnConcurrentModificationException() {
        when(resourceLocationDeleteService.resumeStale(1, 10L))
                .thenThrow(new ConcurrentModificationException("旧租约已被新租约接管"));

        PoetryResult<ResourceLocationDeleteResult> result = controller.resumeDelete(1, 10L);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(409);
        assertThat(result.getMessage()).contains("旧租约已被新租约接管");
    }

    @Test
    void resumeDeleteShouldReturn409WhenServiceReturnsDeletingStatus() {
        ResourceLocationDeleteResult serviceResult = new ResourceLocationDeleteResult(
                1, 10L, 20L, "DELETING", false, false, "租约续删中，仍需等待回读");
        when(resourceLocationDeleteService.resumeStale(1, 10L)).thenReturn(serviceResult);

        PoetryResult<ResourceLocationDeleteResult> result = controller.resumeDelete(1, 10L);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getCode()).isEqualTo(409);
        assertThat(result.getData()).isSameAs(serviceResult);
    }

    @Test
    void resumeDeleteShouldReturnSuccessWhenRecordMarkedRemoved() {
        ResourceLocationDeleteResult serviceResult = new ResourceLocationDeleteResult(
                1, 10L, 20L, "MISSING", false, true, "副本确认缺失，记录已标记移除");
        when(resourceLocationDeleteService.resumeStale(1, 10L)).thenReturn(serviceResult);

        PoetryResult<ResourceLocationDeleteResult> result = controller.resumeDelete(1, 10L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).isSameAs(serviceResult);
    }
}
