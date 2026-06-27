package com.ld.poetry.controller;

import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.controller.dto.RagPreviewRequest;
import com.ld.poetry.service.SysAiConfigService;
import com.ld.poetry.service.ai.AiImageService;
import com.ld.poetry.service.ai.rag.RagSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysAiConfigControllerTest {

    @Mock
    private SysAiConfigService sysAiConfigService;

    @Mock
    private RagSyncService ragSyncService;

    @Mock
    private AiImageService aiImageService;

    private SysAiConfigController controller;

    @BeforeEach
    void setUp() {
        controller = new SysAiConfigController(sysAiConfigService, ragSyncService, aiImageService);
    }

    @Test
    void rebuildShouldFailFastWhenRagIsBlocked() {
        when(ragSyncService.getBlockingReason()).thenReturn("RAG 当前未启用");

        PoetryResult<Boolean> result = controller.rebuildAiChatRag();

        assertEquals(500, result.getCode());
        assertFalse(result.isSuccess());
        assertEquals("RAG 当前未启用", result.getMessage());
        verify(ragSyncService, never()).rebuildAllAsync();
    }

    @Test
    void previewShouldFailFastWhenRagIsBlocked() {
        when(ragSyncService.getBlockingReason()).thenReturn("Embedding 模型未配置。");

        PoetryResult<Map<String, Object>> result = controller.previewAiChatRag(
                new RagPreviewRequest("测试问题", Map.of()));

        assertEquals(500, result.getCode());
        assertFalse(result.isSuccess());
        assertEquals("Embedding 模型未配置。", result.getMessage());
        verify(ragSyncService, never()).preview("测试问题", Map.of());
    }
}
