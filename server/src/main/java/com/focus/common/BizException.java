package com.focus.common;

/** 业务异常，携带统一错误码 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public static BizException badRequest(String message) {
        return new BizException(CommonResult.CODE_BAD_REQUEST, message);
    }

    public static BizException notFound(String message) {
        return new BizException(CommonResult.CODE_NOT_FOUND, message);
    }

    public int getCode() {
        return code;
    }
}
