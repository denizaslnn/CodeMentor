package com.codementor.codeservice.exception;

import lombok.Getter;

@Getter
public class AppException extends RuntimeException {
    private final String messageKey;   // messages.properties anahtarı
    private final String errorCode;    // frontend'in gördüğü sabit kod
    private final Object[] args;       // {0}, {1} için değerler

    public AppException(String messageKey, String errorCode, Object... args) {
        super(messageKey);             // log'da anahtar görünsün
        this.messageKey = messageKey;
        this.errorCode = errorCode;
        this.args = args;
    }

    public AppException(String messageKey, String errorCode, Throwable cause, Object... args) {
        super(messageKey, cause);      // cause KAYBOLMASIN
        this.messageKey = messageKey;
        this.errorCode = errorCode;
        this.args = args;
    }
}
