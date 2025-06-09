package com.ld.poetry.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 配置index.html和其他HTML文件
        registry.addResourceHandler("/*.html")
                .addResourceLocations("file:./", 
                                    "file:../", 
                                    "file:../../../",
                                    "classpath:/static/")
                .setCachePeriod(0); // 不缓存HTML文件，确保更新及时

        // 配置静态资源（CSS, JS, 图片等）
        registry.addResourceHandler("/static/**")
                .addResourceLocations("file:./static/", 
                                    "file:../static/", 
                                    "file:../../../static/",
                                    "classpath:/static/")
                .setCachePeriod(3600);
                
        registry.addResourceHandler("/css/**")
                .addResourceLocations("file:./css/", 
                                    "file:../css/", 
                                    "file:../../../css/",
                                    "classpath:/static/css/")
                .setCachePeriod(3600);
                
        registry.addResourceHandler("/js/**")
                .addResourceLocations("file:./js/", 
                                    "file:../js/", 
                                    "file:../../../js/",
                                    "classpath:/static/js/")
                .setCachePeriod(3600);
                
        registry.addResourceHandler("/libs/**")
                .addResourceLocations("file:./libs/", 
                                    "file:../libs/", 
                                    "file:../../../libs/",
                                    "classpath:/static/libs/")
                .setCachePeriod(3600);

        // 配置图片和其他资源
        registry.addResourceHandler("/*.jpg", "/*.png", "/*.gif", "/*.ico", "/*.svg")
                .addResourceLocations("file:./", 
                                    "file:../", 
                                    "file:../../../",
                                    "classpath:/static/")
                .setCachePeriod(3600);
    }
} 