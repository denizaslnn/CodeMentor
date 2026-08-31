package com.codementor.aiservice.exception;

import com.codementor.aiservice.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MessageSource messageSource;

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException ex) {
        return build(ex, HttpStatus.NOT_FOUND, false);
    }

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleApp(AppException ex) {
        return build(ex, HttpStatus.INTERNAL_SERVER_ERROR, true);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ApiResponse<Void>> handleValidation(BindException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", detail);
        return ResponseEntity.badRequest()
                .body(ApiResponse.<Void>builder()
                        .success(false)
                        .message(detail)
                        .errorCode("VALIDATION_FAILED")
                        .httpStatusCode(400)
                        .build());
    }

    @ExceptionHandler({SQLException.class, DataAccessException.class})
    public ResponseEntity<ApiResponse<Void>> handleDatabase(Exception ex) {
        log.error("Database error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(500).body(ApiResponse.error(
                "error.generic.database", "DATABASE_ERROR", 500));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex, HttpServletRequest request) {
        if (isClientAbort(ex)) {
            log.debug("Client disconnected: {}", ex.getMessage());
            return ResponseEntity.noContent().build();
        }
        log.error("Unhandled error at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(500).body(ApiResponse.error(
                "error.generic.unexpected", "INTERNAL_ERROR", 500));
    }

    private ResponseEntity<ApiResponse<Void>> build(AppException ex, HttpStatus status, boolean serverFault) {
        if (serverFault) {
            log.error("{} -> {} key={}", ex.getClass().getSimpleName(), status, ex.getMessageKey(), ex);
        } else {
            log.warn("{} -> {} key={} args={}", ex.getClass().getSimpleName(), status,
                    ex.getMessageKey(), java.util.Arrays.toString(ex.getArgs()));
        }
        return ResponseEntity.status(status).body(ApiResponse.error(ex.getMessageKey(), ex.getErrorCode(), status.value(), ex.getArgs()));
    }

    private String msg(String key, String fallback, Object... args) {
        try {
            return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
        } catch (Exception e) {
            log.warn("Missing message key: {}", key);
            return fallback;
        }
    }

    private static boolean isClientAbort(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            String m = c.getMessage();
            if (c.getClass().getName().contains("ClientAbortException")
                    || (m != null && (m.contains("Broken pipe") || m.contains("Connection reset")))) {
                return true;
            }
        }
        return false;
    }
}
