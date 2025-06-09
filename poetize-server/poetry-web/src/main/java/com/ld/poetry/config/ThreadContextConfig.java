package com.ld.poetry.config;

import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.entity.User;
import com.ld.poetry.utils.PoetryUtil;
import com.ld.poetry.utils.cache.PoetryCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 线程上下文配置类
 * 解决线程间用户信息传递问题
 */
@Configuration
@Slf4j
public class ThreadContextConfig implements AsyncConfigurer {

    /**
     * 上下文传递装饰器
     * 负责在线程间传递用户Token和请求上下文
     */
    @Bean
    public TaskDecorator contextPropagatingTaskDecorator() {
        return task -> {
            // 获取主线程的请求上下文
            RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
            
            // 获取主线程的用户Token
            String token = PoetryUtil.getToken();
            User currentUser = null;
            if (StringUtils.hasText(token)) {
                currentUser = (User) PoetryCache.get(token);
            }
            
            // 保存最终用户对象，供内部类使用
            final User finalUser = currentUser;
            
            return () -> {
                try {
                    // 在新线程设置请求上下文
                    if (requestAttributes != null) {
                        RequestContextHolder.setRequestAttributes(requestAttributes);
                    }
                    
                    // 模拟将用户信息存入线程本地变量
                    if (finalUser != null && StringUtils.hasText(token)) {
                        // 这样在新线程中PoetryUtil.getUserId()就能正常工作
                        // 通过在请求上下文中添加Token信息
                        if (requestAttributes != null) {
                            requestAttributes.setAttribute(CommonConst.TOKEN_HEADER, token, RequestAttributes.SCOPE_REQUEST);
                        }
                        log.debug("线程上下文传递 - Token: {}, 用户ID: {}", token, finalUser.getId());
                    }
                    
                    // 执行原始任务
                    task.run();
                } finally {
                    // 清理线程上下文
                    RequestContextHolder.resetRequestAttributes();
                }
            };
        };
    }

    /**
     * 配置默认的异步执行器
     * 所有@Async注解的方法将使用此执行器
     */
    @Override
    @Bean(name = "taskExecutor")
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 核心线程数
        executor.setCorePoolSize(5);
        // 最大线程数
        executor.setMaxPoolSize(10);
        // 队列容量
        executor.setQueueCapacity(25);
        // 线程名前缀
        executor.setThreadNamePrefix("Poetize-Async-");
        // 拒绝策略
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 等待所有任务结束后再关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);
        // 应用上下文传递装饰器
        executor.setTaskDecorator(contextPropagatingTaskDecorator());
        
        executor.initialize();
        return executor;
    }

    /**
     * 异步方法异常处理器
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> {
            log.error("异步方法执行异常 - 方法: {}, 参数: {}, 异常: {}", 
                    method.getName(), 
                    params, 
                    ex.getMessage(), 
                    ex);
        };
    }
} 