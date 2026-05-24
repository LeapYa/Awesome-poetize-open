package com.ld.poetry.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.entity.ThirdPartyOauthConfig;
import com.ld.poetry.entity.dto.ThirdPartyOauthPublicConfigDTO;
import com.ld.poetry.service.ThirdPartyOauthConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ThirdPartyOauthConfigControllerTest {

    @Mock
    private ThirdPartyOauthConfigService thirdPartyOauthConfigService;

    @InjectMocks
    private ThirdPartyOauthConfigController controller;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void enabledConfigsRequireAdminAndDoNotExposeSecrets() throws Exception {
        when(thirdPartyOauthConfigService.getEnabledConfigs()).thenReturn(List.of(oauthConfig()));

        PoetryResult<List<ThirdPartyOauthPublicConfigDTO>> result = controller.getEnabledConfigs();
        String json = objectMapper.writeValueAsString(result);

        assertAdminOnly("getEnabledConfigs");
        assertTrue(result.isSuccess());
        assertEquals("github", result.getData().getFirst().getPlatformType());
        assertSecretsHidden(json);
    }

    @Test
    void activeConfigsRequireAdminAndDoNotExposeSecrets() throws Exception {
        when(thirdPartyOauthConfigService.getActiveConfigs()).thenReturn(List.of(oauthConfig()));

        PoetryResult<List<ThirdPartyOauthPublicConfigDTO>> result = controller.getActiveConfigs();
        String json = objectMapper.writeValueAsString(result);

        assertAdminOnly("getActiveConfigs");
        assertTrue(result.isSuccess());
        assertEquals("github", result.getData().getFirst().getPlatformType());
        assertSecretsHidden(json);
    }

    private ThirdPartyOauthConfig oauthConfig() {
        ThirdPartyOauthConfig config = new ThirdPartyOauthConfig();
        config.setId(1);
        config.setPlatformType("github");
        config.setPlatformName("GitHub");
        config.setClientId("public-client-id");
        config.setClientSecret("super-secret-client-secret");
        config.setClientKey("oauth-client-key");
        config.setRedirectUri("https://example.com/callback/github");
        config.setScope("user:email");
        config.setEnabled(true);
        config.setGlobalEnabled(true);
        config.setSortOrder(1);
        config.setRemark("GitHub OAuth");
        return config;
    }

    private void assertAdminOnly(String methodName) throws NoSuchMethodException {
        Method method = ThirdPartyOauthConfigController.class.getDeclaredMethod(methodName);
        LoginCheck loginCheck = method.getAnnotation(LoginCheck.class);

        assertNotNull(loginCheck);
        assertEquals(0, loginCheck.value());
    }

    private void assertSecretsHidden(String json) {
        assertFalse(json.contains("clientSecret"));
        assertFalse(json.contains("client_secret"));
        assertFalse(json.contains("clientKey"));
        assertFalse(json.contains("super-secret-client-secret"));
        assertFalse(json.contains("oauth-client-key"));
    }
}
