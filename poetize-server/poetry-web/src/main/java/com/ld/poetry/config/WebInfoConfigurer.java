package com.ld.poetry.config;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Component
public class WebInfoConfigurer implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new WebInfoHandlerInterceptor())
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
                    "/webInfo/getWebInfo", 
                    "/webInfo/updateWebInfo",
                    "/webInfo/getSortInfo",
                    "/webInfo/getAdmire",
                    "/webInfo/getWaifuJson",
                    "/webInfo/getApiConfig",
                    "/sysConfig/listSysConfig"
                );
    }
}
