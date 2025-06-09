package com.ld.poetry.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.task.ThreadPoolTaskExecutorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Java 21 虚拟线程配置
 * 
 * @author LeapYa Team
 * @since 3.0-LeapYa
 */
@Configuration
@EnableAsync
public class VirtualThreadsConfig {

    /**
     * 配置虚拟线程执行器作为主要的任务执行器
     */
    @Bean(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME)
    @Primary
    public AsyncTaskExecutor applicationTaskExecutor() {
        return new VirtualThreadTaskExecutor("virtual-task-");
    }

    /**
     * 为异步方法配置虚拟线程执行器
     */
    @Bean("asyncExecutor")
    public Executor asyncExecutor() {
        return new VirtualThreadTaskExecutor("async-virtual-");
    }

    /**
     * 为定时任务配置虚拟线程执行器
     */
    @Bean("scheduledExecutor")
    public Executor scheduledExecutor() {
        return new VirtualThreadTaskExecutor("scheduled-virtual-");
    }

    /**
     * 传统线程池作为备用（当虚拟线程不可用时）
     */
    @Bean("fallbackExecutor")
    @ConditionalOnProperty(name = "spring.threads.virtual.enabled", havingValue = "false")
    public TaskExecutor fallbackExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("fallback-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
} 