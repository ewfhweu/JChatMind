package com.kama.jchatmind.exception;

import com.kama.jchatmind.model.common.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 捕获业务异常，错误信息返回给前端
     */
    @ExceptionHandler(BizException.class)
    public ApiResponse<Void> handleBizException(BizException e, HttpServletRequest request) {
        log.warn("业务异常: uri={}, code={}, message={}", 
                request.getRequestURI(), e.getCode(), e.getMessage());
        return ApiResponse.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理参数校验异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValidationException(MethodArgumentNotValidException e, 
            HttpServletRequest request) {
        String errorMsg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("参数校验异常: uri={}, message={}", request.getRequestURI(), errorMsg);
        return ApiResponse.error(ErrorCode.PARAM_INVALID.getCode(), errorMsg);
    }

    /**
     * 处理参数绑定异常
     */
    @ExceptionHandler(BindException.class)
    public ApiResponse<Void> handleBindException(BindException e, HttpServletRequest request) {
        String errorMsg = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("参数绑定异常: uri={}, message={}", request.getRequestURI(), errorMsg);
        return ApiResponse.error(ErrorCode.PARAM_INVALID.getCode(), errorMsg);
    }

    /**
     * 处理缺少参数异常
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ApiResponse<Void> handleMissingParameterException(
            MissingServletRequestParameterException e, HttpServletRequest request) {
        log.warn("缺少请求参数: uri={}, parameter={}", 
                request.getRequestURI(), e.getParameterName());
        return ApiResponse.error(ErrorCode.PARAM_MISSING.getCode(), 
                "缺少必要参数: " + e.getParameterName());
    }

    /**
     * 处理参数类型不匹配异常
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ApiResponse<Void> handleTypeMismatchException(
            MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        log.warn("参数类型不匹配: uri={}, parameter={}, requiredType={}", 
                request.getRequestURI(), e.getName(), e.getRequiredType());
        return ApiResponse.error(ErrorCode.PARAM_INVALID.getCode(), 
                "参数类型不正确: " + e.getName());
    }

    /**
     * 处理 404 错误
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<?> handle404(NoResourceFoundException e, HttpServletRequest request) {
        log.warn("资源不存在: uri={}", request.getRequestURI());
        return ResponseEntity.notFound().build();
    }

    /**
     * 处理请求路径不存在
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ApiResponse<Void> handleNoHandlerFoundException(
            NoHandlerFoundException e, HttpServletRequest request) {
        log.warn("请求路径不存在: uri={}", request.getRequestURI());
        return ApiResponse.error(ErrorCode.NOT_FOUND.getCode(), 
                "请求路径不存在: " + e.getRequestURL());
    }

    /**
     * 处理非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleIllegalArgumentException(
            IllegalArgumentException e, HttpServletRequest request) {
        log.warn("非法参数异常: uri={}, message={}", 
                request.getRequestURI(), e.getMessage());
        return ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), e.getMessage());
    }

    /**
     * 捕获所有未处理的异常, 对前端不返回错误信息
     */
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("服务器内部错误: uri={}", request.getRequestURI(), e);
        return ApiResponse.error(ErrorCode.ERROR.getCode(), "服务器内部错误");
    }

    /**
     * 捕获所有未处理的错误
     */
    @ExceptionHandler(Throwable.class)
    public ApiResponse<Void> handleThrowable(Throwable e, HttpServletRequest request) {
        log.error("未处理的错误: uri={}", request.getRequestURI(), e);
        return ApiResponse.error(ErrorCode.ERROR.getCode(), "服务器内部错误");
    }
}
