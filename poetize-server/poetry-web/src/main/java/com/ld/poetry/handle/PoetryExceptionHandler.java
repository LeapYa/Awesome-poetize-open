package com.ld.poetry.handle;

import com.ld.poetry.utils.JsonUtils;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.enums.CodeMsg;
import com.ld.poetry.service.ai.SseRequestUtils;
import com.ld.poetry.utils.PoetryUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.stream.Collectors;
import java.io.IOException;

/**
 * 全局异常处理器
 * 
 * <p>统一处理系统中抛出的各类异常，根据异常类型返回不同的响应结果。
 * 异常处理优先级从高到低依次为：登录异常 > 业务异常 > 参数校验异常 > 系统异常
 * 
 * <p>HTTP状态码语义：
 * <ul>
 *   <li>401 - 未认证（token为空、无效或过期）</li>
 *   <li>403 - 权限不足（已认证但无权访问）</li>
 *   <li>429 - 请求过于频繁（触发限流）</li>
 *   <li>500 - 服务内部错误</li>
 * </ul>
 * 
 * @author sara (原作者)
 * @author LeapYa (优化者)
 */
@ControllerAdvice
@Slf4j
public class PoetryExceptionHandler {

    /**
     * 处理限流异常
     * 
     * <p>返回429状态码，并设置Retry-After响应头</p>
     */
    @ExceptionHandler(RateLimitException.class)
    @ResponseBody
    public PoetryResult handleRateLimitException(RateLimitException ex, HttpServletResponse response) {
        HttpServletRequest request = PoetryUtil.getRequest();
        String requestUrl = request != null ? request.getRequestURL().toString() : "unknown";
        String clientIp = PoetryUtil.getCurrentClientIp();
        
        log.warn("[限流] IP: {}, URL: {}, 限流器: {}, Key: {}, 重试间隔: {}s", 
            clientIp, requestUrl, ex.getLimitName(), ex.getLimitKey(), ex.getRetryAfterSeconds());
        
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(ex.getRetryAfterSeconds()));
        
        return PoetryResult.fail(CodeMsg.RATE_LIMITED.getCode(), ex.getMessage());
    }

    /**
     * 专门处理内容协商异常
     */
    @ExceptionHandler(value = org.springframework.web.HttpMediaTypeNotAcceptableException.class, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public PoetryResult handleHttpMediaTypeNotAcceptableException(
            org.springframework.web.HttpMediaTypeNotAcceptableException ex, HttpServletResponse response) {
        HttpServletRequest request = PoetryUtil.getRequest();
        String requestUrl = request != null ? request.getRequestURL().toString() : "unknown";
        String clientIp = PoetryUtil.getCurrentClientIp();
        String acceptHeader = request != null ? request.getHeader("Accept") : "null";
        String userAgent = request != null ? request.getHeader("User-Agent") : "null";

        log.warn("内容协商失败 - IP: {}, URL: {}, Accept: {}, User-Agent: {}, 原因: {}",
                clientIp, requestUrl, acceptHeader, userAgent, ex.getMessage());

        response.setStatus(HttpServletResponse.SC_NOT_ACCEPTABLE);
        return PoetryResult.fail(406, "请求的响应格式不支持，请使用JSON格式");
    }

    /**
     * 全局异常统一处理入口
     * 
     * @param ex 捕获的异常对象
     * @return 封装的错误响应结果
     */
    @ExceptionHandler(Exception.class)
    @ResponseBody
    public Object handleException(Exception ex, HttpServletResponse response) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes != null ? attributes.getRequest() : PoetryUtil.getRequest();
        String requestUrl = request != null ? request.getRequestURL().toString() : "unknown";

        if (SseRequestUtils.isSseRequest(request, response)) {
            if (SseRequestUtils.isClientCancellation(ex) || ex instanceof IOException) {
                log.info("SSE连接已主动断开 - URL: {}, 原因: {}", requestUrl, ex.getMessage());
            } else {
                log.error("SSE请求异常 - URL: {}", requestUrl, ex);
            }
            return null;
        }

        // 登录异常：未认证，返回 HTTP 401
        if (ex instanceof PoetryLoginException) {
            log.warn("登录验证失败 - URL: {}, 原因: {}", requestUrl, ex.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return PoetryResult.fail(CodeMsg.LOGIN_EXPIRED.getCode(), ex.getMessage());
        }

        // 记录异常详情供排查问题
        log.error("请求异常 - URL: {}", requestUrl);
        log.error("异常详情：", ex);

        // 业务运行时异常：返回业务错误信息
        if (ex instanceof PoetryRuntimeException) {
            // 权限不足场景返回 HTTP 403
            if (ex.getMessage() != null && ex.getMessage().contains("权限不足")) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return PoetryResult.fail(CodeMsg.FORBIDDEN.getCode(), ex.getMessage());
            }
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return PoetryResult.fail(ex.getMessage());
        }

        // 参数校验异常：收集所有字段错误信息并返回
        if (ex instanceof MethodArgumentNotValidException) {
            MethodArgumentNotValidException validEx = (MethodArgumentNotValidException) ex;
            Map<String, String> fieldErrors = validEx.getFieldErrors()
                    .stream()
                    .collect(Collectors.toMap(
                            FieldError::getField,
                            FieldError::getDefaultMessage
                    ));
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return PoetryResult.fail(CodeMsg.PARAMETER_ERROR.getCode(), JsonUtils.toJsonString(fieldErrors));
        }

        // 缺少必填参数异常
        if (ex instanceof MissingServletRequestParameterException) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return PoetryResult.fail(CodeMsg.PARAMETER_ERROR);
        }

        // 未知异常：返回通用错误信息，不暴露内部细节
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        return PoetryResult.fail(CodeMsg.FAIL);
    }
}
