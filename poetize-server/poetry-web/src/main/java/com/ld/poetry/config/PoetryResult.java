package com.ld.poetry.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ld.poetry.enums.CodeMsg;
import lombok.Data;

import java.io.Serializable;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PoetryResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private int code;
    private String message;
    private T data;
    private boolean success;

    public PoetryResult() {
        this.code = 200;
        this.success = true;
    }

    public PoetryResult(int code, String message) {
        this.code = code;
        this.message = message;
        this.success = (code == 200);
    }

    public PoetryResult(T data) {
        this.code = 200;
        this.data = data;
        this.success = true;
    }

    public PoetryResult(String message) {
        this.code = 500;
        this.message = message;
        this.success = false;
    }

    public static <T> PoetryResult<T> fail(String message) {
        return new PoetryResult<>(message);
    }

    public static <T> PoetryResult<T> fail(CodeMsg codeMsg) {
        return new PoetryResult<>(codeMsg.getCode(), codeMsg.getMsg());
    }

    public static <T> PoetryResult<T> fail(Integer code, String message) {
        return new PoetryResult<>(code, message);
    }

    public static <T> PoetryResult<T> fail(CodeMsg codeMsg, T data) {
        PoetryResult<T> result = new PoetryResult<>(codeMsg.getCode(), codeMsg.getMsg());
        result.setData(data);
        return result;
    }

    public static <T> PoetryResult<T> fail(Integer code, String message, T data) {
        PoetryResult<T> result = new PoetryResult<>(code, message);
        result.setData(data);
        return result;
    }

    public static <T> PoetryResult<T> success(T data) {
        return new PoetryResult<>(data);
    }

    public static <T> PoetryResult<T> success() {
        return new PoetryResult<>();
    }

    /**
     * 判断是否成功
     * 优先使用success字段，如果未设置则根据code判断
     */
    public boolean isSuccess() {
        // 为了向后兼容，如果success字段与code不一致，以code为准
        boolean codeSuccess = (this.code == 200);
        if (this.success != codeSuccess) {
            this.success = codeSuccess;
        }
        return this.success;
    }

    /**
     * 设置成功状态
     * 同时更新code和success字段以保持一致性
     */
    public void setSuccess(boolean success) {
        this.success = success;
        // 保持code和success的一致性
        if (success && this.code != 200) {
            this.code = 200;
        } else if (!success && this.code == 200) {
            this.code = 500;
        }
    }
}
