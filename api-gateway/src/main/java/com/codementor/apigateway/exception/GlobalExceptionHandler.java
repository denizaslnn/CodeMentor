package com.codementor.apigateway.exception;

import com.codementor.apigateway.config.LocalizedMessageResolver;
import com.codementor.apigateway.dto.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final LocalizedMessageResolver localizedMessageResolver;

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException ex, ServerWebExchange exchange) {
        return build(ex, HttpStatus.NOT_FOUND, exchange, false);
    }

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppException(AppException ex, ServerWebExchange exchange) {
        return build(ex, HttpStatus.INTERNAL_SERVER_ERROR, exchange, true);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                          ServerWebExchange exchange) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return validationFailed(exchange, detail);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBindException(BindException ex, ServerWebExchange exchange) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return validationFailed(exchange, detail);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex,
                                                                       ServerWebExchange exchange) {
        String detail = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining("; "));
        return validationFailed(exchange, detail);
    }

    // NOT: Spring Security'nin AuthenticationException/AccessDeniedException
    // handler'lari buradan cikarildi. Gateway'de spring-security bagimliligi yok;
    // kimlik dogrulama JwtGlobalFilter/JwtAuthenticationFilter tarafindan yapilir ve
    // 401/403 govdeleri UnauthorizedResponseWriter uzerinden ayni ApiResponse
    // formatinda yazilir (error.auth.unauthorized / error.auth.forbidden ayni
    // message key'leri oradan kullanilir).

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex,
                                                                   ServerWebExchange exchange) {
        log.warn("Invalid request argument at path={}: {}", exchange.getRequest().getPath().value(), ex.getClass().getSimpleName());
        return error(HttpStatus.BAD_REQUEST, "error.generic.invalid.request", "INVALID_REQUEST", exchange);
    }

    /**
     * WebFlux karsiligi: servlet'e ozgu NoHandlerFoundException/NoResourceFoundException
     * reaktif stack'te yoktur, eslesmeyen istek ResponseStatusException olarak gelir.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatus(ResponseStatusException ex, ServerWebExchange exchange) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        if (status == HttpStatus.NOT_FOUND) {
            log.warn("No route found for path={}", exchange.getRequest().getPath().value());
            return error(status, "error.resource.notfound", "NOT_FOUND", exchange);
        }
        log.warn("Request failed at path={} with status={}", exchange.getRequest().getPath().value(), status);
        return error(status, "error.generic.unexpected", "INTERNAL_ERROR", exchange);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex, ServerWebExchange exchange) {
        log.error("Unhandled exception at path={}", exchange.getRequest().getPath().value(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "error.generic.unexpected", "INTERNAL_ERROR", exchange);
    }

    private ResponseEntity<ApiResponse<Void>> build(AppException ex, HttpStatus status, ServerWebExchange exchange, boolean serverFault) {
        if (serverFault) {
            log.error("{} -> {} key={}", ex.getClass().getSimpleName(), status, ex.getMessageKey(), ex);
        } else {
            log.warn("{} -> {} key={} args={}", ex.getClass().getSimpleName(), status,
                    ex.getMessageKey(), java.util.Arrays.toString(ex.getArgs()));
        }

        String message = localizedMessageResolver.resolve(ex.getMessageKey(), exchange, ex.getArgs());
        return ResponseEntity.status(status).body(ApiResponse.<Void>builder()
                .success(false)
                .message(message)
                .errorCode(ex.getErrorCode())
                .httpStatusCode(status.value())
                .build());
    }

    private ResponseEntity<ApiResponse<Void>> validationFailed(ServerWebExchange exchange, String detail) {
        log.warn("Validation failed at path={}: {}", exchange.getRequest().getPath().value(), detail);
        return error(HttpStatus.BAD_REQUEST, "error.validation.failed", "VALIDATION_FAILED", exchange);
    }

    private ResponseEntity<ApiResponse<Void>> error(HttpStatus status, String messageKey, String errorCode,
                                                    ServerWebExchange exchange, Object... args) {
        String message = localizedMessageResolver.resolve(messageKey, exchange, args);
        return ResponseEntity.status(status).body(ApiResponse.<Void>builder()
                .success(false)
                .message(message)
                .errorCode(errorCode)
                .httpStatusCode(status.value())
                .build());
    }
}
