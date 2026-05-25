package com.ld.poetry.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.dao.ThirdPartyOauthConfigMapper;
import com.ld.poetry.entity.ThirdPartyOauthConfig;
import com.ld.poetry.service.impl.ThirdPartyOauthConfigServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 第三方OAuth配置服务测试类
 *
 * @author sara
 * @since 2025-07-19
 */
@ExtendWith(MockitoExtension.class)
class ThirdPartyOauthConfigServiceTest {

    @Mock
    private ThirdPartyOauthConfigMapper configMapper;

    @Mock
    private ObjectMapper objectMapper;

    @Spy
    @InjectMocks
    private ThirdPartyOauthConfigServiceImpl configService;

    private ThirdPartyOauthConfig githubConfig;
    private ThirdPartyOauthConfig googleConfig;
    private List<ThirdPartyOauthConfig> allConfigs;

    @BeforeEach
    void setUp() {
        githubConfig = new ThirdPartyOauthConfig();
        githubConfig.setId(1);
        githubConfig.setPlatformType("github");
        githubConfig.setPlatformName("GitHub");
        githubConfig.setClientId("test_client_id");
        githubConfig.setClientSecret("test_client_secret");
        githubConfig.setRedirectUri("http://localhost:8080/oauth/callback/github");
        githubConfig.setEnabled(true);
        githubConfig.setGlobalEnabled(true);
        githubConfig.setSortOrder(1);
        githubConfig.setCreateTime(LocalDateTime.now());
        githubConfig.setUpdateTime(LocalDateTime.now());
        githubConfig.setDeleted(false);

        googleConfig = new ThirdPartyOauthConfig();
        googleConfig.setId(2);
        googleConfig.setPlatformType("google");
        googleConfig.setPlatformName("Google");
        googleConfig.setClientId("google_client_id");
        googleConfig.setClientSecret("google_client_secret");
        googleConfig.setRedirectUri("http://localhost:8080/oauth/callback/google");
        googleConfig.setEnabled(true);
        googleConfig.setGlobalEnabled(true);
        googleConfig.setSortOrder(2);
        googleConfig.setCreateTime(LocalDateTime.now());
        googleConfig.setUpdateTime(LocalDateTime.now());
        googleConfig.setDeleted(false);

        allConfigs = Arrays.asList(githubConfig, googleConfig);
    }

    @Test
    void testGetByPlatformType() {
        // Given
        when(configMapper.getByPlatformType("github")).thenReturn(githubConfig);

        // When
        ThirdPartyOauthConfig result = configService.getByPlatformType("github");

        // Then
        assertNotNull(result);
        assertEquals("github", result.getPlatformType());
        assertEquals("GitHub", result.getPlatformName());
        verify(configMapper).getByPlatformType("github");
    }

    @Test
    void testGetByPlatformTypeWithEmptyType() {
        // When
        ThirdPartyOauthConfig result = configService.getByPlatformType("");

        // Then
        assertNull(result);
        verify(configMapper, never()).getByPlatformType(anyString());
    }

    @Test
    void testGetEnabledConfigs() {
        // Given
        when(configMapper.getEnabledConfigs()).thenReturn(allConfigs);

        // When
        List<ThirdPartyOauthConfig> result = configService.getEnabledConfigs();

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(ThirdPartyOauthConfig::getEnabled));
        verify(configMapper).getEnabledConfigs();
    }

    @Test
    void testGetActiveConfigs() {
        // Given
        when(configMapper.getActiveConfigs()).thenReturn(allConfigs);

        // When
        List<ThirdPartyOauthConfig> result = configService.getActiveConfigs();

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(config -> config.getEnabled() && config.getGlobalEnabled()));
        verify(configMapper).getActiveConfigs();
    }

    @Test
    void testUpdateGlobalEnabledSuccess() {
        // Given
        when(configMapper.updateGlobalEnabled(true)).thenReturn(5);

        // When
        PoetryResult<Boolean> result = configService.updateGlobalEnabled(true);

        // Then
        assertTrue(result.isSuccess());
        assertTrue(result.getData());
        verify(configMapper).updateGlobalEnabled(true);
    }

    @Test
    void testUpdateGlobalEnabledFailure() {
        // Given
        when(configMapper.updateGlobalEnabled(true)).thenReturn(0);

        // When
        PoetryResult<Boolean> result = configService.updateGlobalEnabled(true);

        // Then
        assertFalse(result.isSuccess());
        assertEquals("更新失败", result.getMessage());
        verify(configMapper).updateGlobalEnabled(true);
    }

    @Test
    void testUpdatePlatformEnabledSuccess() {
        // Given
        when(configMapper.getByPlatformType("github")).thenReturn(githubConfig);
        doReturn(true).when(configService).updateById(any(ThirdPartyOauthConfig.class));

        // When
        PoetryResult<Boolean> result = configService.updatePlatformEnabled("github", true);

        // Then
        assertTrue(result.isSuccess());
        assertTrue(result.getData());
        verify(configMapper).getByPlatformType("github");
        verify(configService).updateById(githubConfig);
    }

    @Test
    void testUpdatePlatformEnabledWithEmptyPlatformType() {
        // When
        PoetryResult<Boolean> result = configService.updatePlatformEnabled("", true);

        // Then
        assertFalse(result.isSuccess());
        assertEquals("平台类型不能为空", result.getMessage());
        verify(configMapper, never()).updatePlatformEnabled(anyString(), anyBoolean());
    }

    @Test
    void testGetThirdLoginConfigSuccess() {
        // Given
        when(configMapper.getAllConfigs()).thenReturn(allConfigs);

        // When
        PoetryResult<Map<String, Object>> result = configService.getThirdLoginConfig();

        // Then
        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
        
        Map<String, Object> data = result.getData();
        assertTrue(data.containsKey("enable"));
        assertTrue(data.containsKey("github"));
        assertTrue(data.containsKey("google"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> githubData = (Map<String, Object>) data.get("github");
        assertEquals("test_client_id", githubData.get("client_id"));
        assertEquals("test_client_secret", githubData.get("client_secret"));
        assertEquals(true, githubData.get("enabled"));
        
        verify(configMapper).getAllConfigs();
    }

    @Test
    void testUpdateThirdLoginConfigSuccess() {
        // Given
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("enable", true);
        
        Map<String, Object> githubConfigMap = new HashMap<>();
        githubConfigMap.put("client_id", "new_client_id");
        githubConfigMap.put("client_secret", "new_client_secret");
        githubConfigMap.put("redirect_uri", "http://localhost:8080/oauth/callback/github");
        githubConfigMap.put("enabled", true);
        configMap.put("github", githubConfigMap);

        when(configMapper.updateGlobalEnabled(true)).thenReturn(1);
        when(configMapper.getByPlatformType("github")).thenReturn(githubConfig);
        doReturn(true).when(configService).updateById(any(ThirdPartyOauthConfig.class));

        // When
        PoetryResult<Boolean> result = configService.updateThirdLoginConfig(configMap);

        // Then
        assertTrue(result.isSuccess());
        assertTrue(result.getData());
        verify(configMapper).updateGlobalEnabled(true);
        verify(configMapper).getByPlatformType("github");
    }

    @Test
    void testValidateConfigs() {
        // Given
        githubConfig.setClientId("valid_id");
        githubConfig.setClientSecret("valid_secret");
        googleConfig.setClientId(""); // Invalid
        
        when(configMapper.getAllConfigs()).thenReturn(allConfigs);

        // When
        PoetryResult<Map<String, Object>> result = configService.validateConfigs();

        // Then
        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
        
        Map<String, Object> data = result.getData();
        assertEquals(2, data.get("total_count"));
        assertEquals(1, data.get("valid_count"));
        assertEquals(1, data.get("invalid_count"));
        
        @SuppressWarnings("unchecked")
        List<String> validPlatforms = (List<String>) data.get("valid_platforms");
        @SuppressWarnings("unchecked")
        List<String> invalidPlatforms = (List<String>) data.get("invalid_platforms");
        
        assertTrue(validPlatforms.contains("github"));
        assertTrue(invalidPlatforms.contains("google"));
        
        verify(configMapper).getAllConfigs();
    }

    @Test
    void testGetConfigStats() {
        // Given
        Map<String, Object> stats = new HashMap<>();
        stats.put("total_count", 5);
        stats.put("enabled_count", 4);
        stats.put("global_enabled_count", 3);
        stats.put("active_count", 2);
        
        when(configMapper.getConfigStats()).thenReturn(stats);

        // When
        PoetryResult<Map<String, Object>> result = configService.getConfigStats();

        // Then
        assertTrue(result.isSuccess());
        assertEquals(stats, result.getData());
        verify(configMapper).getConfigStats();
    }

    @Test
    void testResetToDefault() {
        // Given
        doReturn(true).when(configService).remove((Wrapper<ThirdPartyOauthConfig>) isNull());
        doReturn(PoetryResult.success(true)).when(configService).initDefaultConfigs();

        // When
        PoetryResult<Boolean> result = configService.resetToDefault();

        // Then
        assertTrue(result.isSuccess());
        assertTrue(result.getData());
        verify(configService).remove((Wrapper<ThirdPartyOauthConfig>) isNull());
        verify(configService).initDefaultConfigs();
    }
}
