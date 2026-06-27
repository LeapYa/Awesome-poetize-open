package com.ld.poetry.config;

import com.ld.poetry.utils.security.FileDownloadUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

import java.io.IOException;

@Slf4j
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private SecurityFilter securityFilter;

    @Autowired
    private PoetryFilter poetryFilter;
    
    @Value("${local.uploadUrl:/app/static/}")
    private String uploadUrl;

    /**
     * 注册安全过滤器
     */
    @Bean
    public FilterRegistrationBean<SecurityFilter> securityFilterRegistration() {
        FilterRegistrationBean<SecurityFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(securityFilter);
        registration.addUrlPatterns("/*");
        registration.setName("securityFilter");
        registration.setOrder(1); // 设置过滤器优先级，数字越小优先级越高
        return registration;
    }

    /**
     * 注册访问量统计过滤器
     */
    @Bean
    public FilterRegistrationBean<PoetryFilter> poetryFilterRegistration() {
        FilterRegistrationBean<PoetryFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(poetryFilter);
        registration.addUrlPatterns("/*");
        registration.setName("poetryFilter");
        registration.setOrder(2); // 设置在SecurityFilter之后执行
        return registration;
    }

    /**
     * Spring 静态资源兜底：可执行/脚本类文章附件即使被直接访问，也强制下载。
     */
    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> executableAttachmentHeaderFilterRegistration() {
        FilterRegistrationBean<OncePerRequestFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain filterChain) throws ServletException, IOException {
                if (request.getRequestURI() != null
                        && request.getRequestURI().startsWith("/static/articleFile/")) {
                    if (FileDownloadUtil.shouldForceDownload(request.getRequestURI())) {
                        response.setHeader("Content-Disposition",
                                FileDownloadUtil.contentDispositionAttachment(FileDownloadUtil.fileNameFromPath(request.getRequestURI())));
                        response.setHeader("X-Content-Type-Options", "nosniff");
                    }
                }
                filterChain.doFilter(request, response);
            }
        });
        registration.addUrlPatterns("/static/*");
        registration.setName("executableAttachmentHeaderFilter");
        registration.setOrder(3);
        return registration;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 只处理用户上传的文件资源，不处理前端静态资源
        // ResourceHandlerRegistry 的 file: 位置需要绝对路径，相对路径会被误解析。
        // 这里统一转换为绝对 URI，确保 Windows / Linux 下相对路径配置都能工作。
        String location = uploadUrl;
        if (location.startsWith("file:")) {
            location = location.substring("file:".length());
        }
        File locationFile = new File(location);
        if (!locationFile.isAbsolute()) {
            locationFile = locationFile.getAbsoluteFile();
            log.info("local.uploadUrl 为相对路径，已解析为绝对路径: {} -> {}",
                    uploadUrl, locationFile.getAbsolutePath());
        }
        // toURI().toString() 会生成合法的 file:/... URL（跨平台用 /）
        location = locationFile.toURI().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }

        log.info("配置静态资源映射: /static/** -> {}", location);
        registry.addResourceHandler("/static/**")
                .addResourceLocations(location)
                .setCachePeriod(3600);
    }
} 
