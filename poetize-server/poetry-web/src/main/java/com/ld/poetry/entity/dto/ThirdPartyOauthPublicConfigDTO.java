package com.ld.poetry.entity.dto;

import com.ld.poetry.entity.ThirdPartyOauthConfig;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Third-party OAuth config view that is safe to expose outside secret-management flows.
 */
@Data
@Builder
public class ThirdPartyOauthPublicConfigDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer id;
    private String platformType;
    private String platformName;
    private String clientId;
    private String redirectUri;
    private String scope;
    private Boolean enabled;
    private Boolean globalEnabled;
    private Integer sortOrder;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static ThirdPartyOauthPublicConfigDTO from(ThirdPartyOauthConfig config) {
        if (config == null) {
            return null;
        }
        return ThirdPartyOauthPublicConfigDTO.builder()
                .id(config.getId())
                .platformType(config.getPlatformType())
                .platformName(config.getPlatformName())
                .clientId(config.getClientId())
                .redirectUri(config.getRedirectUri())
                .scope(config.getScope())
                .enabled(config.getEnabled())
                .globalEnabled(config.getGlobalEnabled())
                .sortOrder(config.getSortOrder())
                .remark(config.getRemark())
                .createTime(config.getCreateTime())
                .updateTime(config.getUpdateTime())
                .build();
    }
}
