package com.focus.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 全局异常处理：所有异常都转成统一响应体 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public CommonResult<Void> handleBiz(BizException e) {
        return CommonResult.error(e.getCode(), e.getMessage());
    }

    /** @Valid 参数校验失败 → 40000 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public CommonResult<Void> handleInvalid(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError == null
                ? "参数错误"
                : fieldError.getField() + " " + fieldError.getDefaultMessage();
        return CommonResult.error(CommonResult.CODE_BAD_REQUEST, message);
    }

    @ExceptionHandler(Exception.class)
    public CommonResult<Void> handleOther(Exception e) {
        log.error("未处理异常", e);
        return CommonResult.error(CommonResult.CODE_SERVER_ERROR, "系统异常");
    }
}
