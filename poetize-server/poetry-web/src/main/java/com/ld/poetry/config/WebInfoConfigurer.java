package com.ld.poetry.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Component
public class WebInfoConfigurer implements WebMvcConfigurer {

    @Autowired
    private WebInfoHandlerInterceptor webInfoHandlerInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(webInfoHandlerInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                    "/",
                    "/*.html",
                    "/static/**",
                    "/css/**",
                    "/js/**",
                    "/libs/**",
                    "/*.ico", "/*.png", "/*.jpg", "/*.gif", "/*.svg",
                    "/user/login", 
                    "/admin/**", 
                    "/webInfo/bootstrap",
                    "/webInfo/getWebInfo",
                    "/webInfo/updateWebInfo",
                    "/webInfo/getSortInfo",
                    "/webInfo/getAdmire",
                    "/webInfo/getWaifuJson",
                    "/webInfo/getApiConfig",
                    "/webInfo/getUserIP",
                    "/sysConfig/listSysConfig",
                    "/sysConfig/saveOrUpdateConfig",
                    "/sysConfig/deleteConfig",
                    "/sysConfig/listConfig",
                    "/api/sysConfig/listSysConfig",
                    "/api/sysConfig/saveOrUpdateConfig",
                    "/api/sysConfig/deleteConfig",
                    "/api/sysConfig/listConfig",
                    "/api/webInfo/bootstrap",
                    "/api/webInfo/getUserIP",
                    "/api/track/**"
                );
    }
}
