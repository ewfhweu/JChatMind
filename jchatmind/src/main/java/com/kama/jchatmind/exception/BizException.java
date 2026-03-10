package com.kama.jchatmind.exception;

import lombok.Getter;

/**
 * 业务异常类
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;
    private final ErrorCode errorCode;

    // 使用默认消息
    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.errorCode = errorCode;
    }

    // 自定义消息
    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
        this.errorCode = errorCode;
    }

    // 自定义消息 + 原始异常
    public BizException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.code = errorCode.getCode();
        this.errorCode = errorCode;
    }

    // 原始异常，使用默认消息
    public BizException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.code = errorCode.getCode();
        this.errorCode = errorCode;
    }

    // 便捷方法
    public static BizException of(ErrorCode errorCode) {
        return new BizException(errorCode);
    }

    public static BizException of(ErrorCode errorCode, String message) {
        return new BizException(errorCode, message);
    }

    public static BizException of(ErrorCode errorCode, Throwable cause) {
        return new BizException(errorCode, cause);
    }

    public static BizException of(ErrorCode errorCode, String message, Throwable cause) {
        return new BizException(errorCode, message, cause);
    }
}