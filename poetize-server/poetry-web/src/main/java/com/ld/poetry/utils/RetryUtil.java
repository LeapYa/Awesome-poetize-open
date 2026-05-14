package com.ld.poetry.utils;

import com.ld.poetry.handle.PoetryRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * 重试工具类
 * 用于处理临时性失败的自动重试机制
 */
@Slf4j
public class RetryUtil {

    @FunctionalInterface
    public interface RetryCallback {
        void onRetry(int attempt, int maxAttempts, Throwable throwable);
    }

    /**
     * 默认重试次数
     */
    private static final int DEFAULT_MAX_ATTEMPTS = 3;

    /**
     * 默认重试间隔（毫秒）
     */
    private static final long DEFAULT_RETRY_DELAY = 100;

    /**
     * 最大重试间隔（毫秒）
     */
    private static final long MAX_RETRY_DELAY = 5000;

    /**
     * 执行带重试的操作
     *
     * @param operation 要执行的操作
     * @param <T>       返回类型
     * @return 操作结果
     */
    public static <T> T executeWithRetry(Supplier<T> operation) {
        return executeWithRetry(operation, DEFAULT_MAX_ATTEMPTS, DEFAULT_RETRY_DELAY, "操作");
    }

    /**
     * 执行带重试的操作
     *
     * @param operation    要执行的操作
     * @param maxAttempts  最大重试次数
     * @param <T>          返回类型
     * @return 操作结果
     */
    public static <T> T executeWithRetry(Supplier<T> operation, int maxAttempts) {
        return executeWithRetry(operation, maxAttempts, DEFAULT_RETRY_DELAY, "操作");
    }

    /**
     * 执行带重试的操作
     *
     * @param operation     要执行的操作
     * @param maxAttempts   最大重试次数
     * @param retryDelay    重试间隔（毫秒）
     * @param operationName 操作名称（用于日志）
     * @param <T>           返回类型
     * @return 操作结果
     */
    public static <T> T executeWithRetry(Supplier<T> operation, int maxAttempts, long retryDelay, String operationName) {
        return executeWithRetry(operation, maxAttempts, retryDelay, operationName, null);
    }

    public static <T> T executeWithRetry(Supplier<T> operation, int maxAttempts, long retryDelay, String operationName,
            RetryCallback retryCallback) {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                T result = operation.get();
                if (attempt > 1) {
                    log.info("{}在第{}次尝试后成功", operationName, attempt);
                }
                return result;
            } catch (Exception e) {
                lastException = e;
                
                // 如果是业务异常，不进行重试
                if (e instanceof PoetryRuntimeException) {
                    throw e;
                }
                
                // 如果是最后一次尝试，抛出异常
                if (attempt == maxAttempts) {
                    log.error("{}在{}次尝试后仍然失败", operationName, maxAttempts, e);
                    break;
                }

                // 计算重试延迟（指数退避 + 随机抖动）
                long delay = calculateRetryDelay(retryDelay, attempt);
                log.warn("{}第{}次尝试失败，{}毫秒后重试: {}", operationName, attempt, delay, e.getMessage());

                if (retryCallback != null) {
                    retryCallback.onRetry(attempt, maxAttempts, e);
                }

                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("重试过程被中断", ie);
                }
            }
        }
        
        // 所有重试都失败了，抛出最后一个异常
        if (lastException instanceof RuntimeException) {
            throw (RuntimeException) lastException;
        } else {
            throw new RuntimeException("操作失败: " + operationName, lastException);
        }
    }

    /**
     * 执行带重试的无返回值操作
     *
     * @param operation 要执行的操作
     */
    public static void executeWithRetryVoid(Runnable operation) {
        executeWithRetryVoid(operation, DEFAULT_MAX_ATTEMPTS, DEFAULT_RETRY_DELAY, "操作");
    }

    /**
     * 执行带重试的无返回值操作
     *
     * @param operation     要执行的操作
     * @param maxAttempts   最大重试次数
     * @param retryDelay    重试间隔（毫秒）
     * @param operationName 操作名称（用于日志）
     */
    public static void executeWithRetryVoid(Runnable operation, int maxAttempts, long retryDelay, String operationName) {
        executeWithRetry(() -> {
            operation.run();
            return null;
        }, maxAttempts, retryDelay, operationName);
    }

    /**
     * 计算重试延迟时间（指数退避 + 随机抖动）
     *
     * @param baseDelay 基础延迟时间
     * @param attempt   当前尝试次数
     * @return 计算后的延迟时间
     */
    private static long calculateRetryDelay(long baseDelay, int attempt) {
        // 指数退避：每次重试延迟时间翻倍
        long exponentialDelay = baseDelay * (1L << (attempt - 1));
        
        // 限制最大延迟时间
        exponentialDelay = Math.min(exponentialDelay, MAX_RETRY_DELAY);
        
        // 添加随机抖动（±25%）
        double jitterFactor = 0.75 + (ThreadLocalRandom.current().nextDouble() * 0.5);
        
        return (long) (exponentialDelay * jitterFactor);
    }

    /**
     * 判断异常是否可以重试
     *
     * @param exception 异常
     * @return 是否可以重试
     */
    public static boolean isRetryableException(Exception exception) {
        // 业务异常不重试
        if (exception instanceof PoetryRuntimeException) {
            return false;
        }
        
        // 中断异常不重试
        if (exception instanceof InterruptedException) {
            return false;
        }
        
        // 空指针异常通常不是临时性问题，不重试
        if (exception instanceof NullPointerException) {
            return false;
        }
        
        // 参数异常不重试
        if (exception instanceof IllegalArgumentException) {
            return false;
        }
        
        String message = exception.getMessage();
        if (StringUtils.hasText(message)) {
            message = message.toLowerCase();
            
            // 网络相关异常可以重试
            if (message.contains("connection") || 
                message.contains("timeout") || 
                message.contains("network") ||
                message.contains("socket")) {
                return true;
            }
            
            // 数据库连接异常可以重试
            if (message.contains("database") || 
                message.contains("connection pool") ||
                message.contains("deadlock")) {
                return true;
            }
        }
        
        // 默认情况下，运行时异常可以重试
        return exception instanceof RuntimeException;
    }
}
