package com.codementor.aiservice.config;

import com.codementor.aiservice.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice
@Slf4j
@RequiredArgsConstructor
public class ApiResponseAdvice implements ResponseBodyAdvice<Object> {

    private final MessageSource messageSource;

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // Controller'lar ApiResponse'u dogrudan dondurur; @ExceptionHandler metotlari ise
        // ResponseEntity<ApiResponse<...>> dondurur. Sadece ApiResponse'a bakmak hata
        // yanitlarini kapsam disinda birakip ham message key'in cliente sizmasina yol acar.
        // Gercek govde tipi beforeBodyWrite icinde kontrol edilir.
        Class<?> type = returnType.getParameterType();
        return ApiResponse.class.isAssignableFrom(type)
                || ResponseEntity.class.isAssignableFrom(type)
                || Object.class.equals(type);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof ApiResponse<?> apiResponse) {
            String messageKey = apiResponse.getMessage();
            if (messageKey != null && !messageKey.isBlank()) {
                String localizedMessage = msg(messageKey, messageKey, apiResponse.getArgs());
                return ApiResponse.builder()
                        .success(apiResponse.isSuccess())
                        .message(localizedMessage)
                        .data(apiResponse.getData())
                        .httpStatusCode(apiResponse.getHttpStatusCode())
                        .errorCode(apiResponse.getErrorCode())
                        .build();
            }
        }
        return body;
    }

    private String msg(String key, String fallback, Object[] args) {
        try {
            return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
        } catch (Exception e) {
            log.trace("Message key not found: {}", key);
            return fallback;
        }
    }
}
