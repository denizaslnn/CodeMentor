package com.codementor.aiservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private int httpStatusCode;
    private String errorCode;
    private Object[] args;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .httpStatusCode(200)
                .build();
    }

    public static <T> ApiResponse<T> success(String messageKey, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(messageKey)
                .data(data)
                .httpStatusCode(200)
                .build();
    }

    public static <T> ApiResponse<T> error(String messageKey, String errorCode, int status) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(messageKey)
                .errorCode(errorCode)
                .httpStatusCode(status)
                .build();
    }

    public static <T> ApiResponse<T> error(String messageKey, String errorCode, int status, Object[] args) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(messageKey)
                .errorCode(errorCode)
                .httpStatusCode(status)
                .args(args)
                .build();
    }
}
