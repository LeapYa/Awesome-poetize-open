package com.ld.poetry.enums;

public enum CodeMsg {
    SUCCESS(200, "成功！"),
    PARAMETER_ERROR(400, "参数异常！"),
    NOT_LOGIN(401, "未登陆，请登陆后再进行操作！"),
    LOGIN_EXPIRED(401, "登录已过期，请重新登陆！"),
    FORBIDDEN(403, "权限不足！"),
    SYSTEM_REPAIR(301, "系统维护中，敬请期待！"),
    CAPTCHA_REQUIRED(460, "需要完成验证码验证"),
    CAPTCHA_INVALID(461, "验证码验证失败"),
    CAPTCHA_BLOCKED(462, "验证码验证过于频繁，已被限制"),
    RATE_LIMITED(429, "操作过于频繁，请稍后再试"),
    FAIL(500, "服务异常！");


    private int code;
    private String msg;

    CodeMsg(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }
}
