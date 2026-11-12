package com.focus.common;

/**
 * 统一响应体：{ code, message, data }
 * 0 成功 / 40000 参数错误 / 40400 不存在 / 50000 系统异常
 */
public class CommonResult<T> {

    public static final int CODE_OK = 0;
    public static final int CODE_BAD_REQUEST = 40000;
    public static final int CODE_NOT_FOUND = 40400;
    public static final int CODE_SERVER_ERROR = 50000;

    private int code;
    private String message;
    private T data;

    public CommonResult() {
    }

    public CommonResult(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> CommonResult<T> ok(T data) {
        return new CommonResult<>(CODE_OK, "ok", data);
    }

    public static <T> CommonResult<T> ok() {
        return new CommonResult<>(CODE_OK, "ok", null);
    }

    public static <T> CommonResult<T> error(int code, String message) {
        return new CommonResult<>(code, message, null);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
