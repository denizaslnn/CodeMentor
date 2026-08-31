package com.codementor.aiservice.exception;

import lombok.Getter;

@Getter
public class AppException extends RuntimeException {
    private final String messageKey;
    private final String errorCode;
    private final Object[] args;

    public AppException(String messageKey, String errorCode, Object... args) {
        super(messageKey);
        this.messageKey = messageKey;
        this.errorCode = errorCode;
        this.args = args;
    }

    public AppException(String messageKey, String errorCode, Throwable cause, Object... args) {
        super(messageKey, cause);
        this.messageKey = messageKey;
        this.errorCode = errorCode;
        this.args = args;
    }
}
