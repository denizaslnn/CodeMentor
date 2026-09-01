package com.codementor.codeservice.exception;

import com.codementor.codeservice.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // --- Domain hataları: status'u burada eşleştir ---
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException ex) {
        return build(ex, HttpStatus.NOT_FOUND, false);
    }

    @ExceptionHandler(TaskNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleTaskNotFound(TaskNotFoundException ex) {
        return build(ex, HttpStatus.NOT_FOUND, false);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidCredentials(InvalidCredentialsException ex) {
        return build(ex, HttpStatus.UNAUTHORIZED, false);
    }

    @ExceptionHandler(RefreshTokenException.class)
    public ResponseEntity<ApiResponse<Void>> handleRefreshToken(RefreshTokenException ex) {
        return build(ex, HttpStatus.UNAUTHORIZED, false);
    }

    @ExceptionHandler(UsernameAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Void>> handleUsernameAlreadyExists(UsernameAlreadyExistsException ex) {
        return build(ex, HttpStatus.CONFLICT, false);
    }

    /** Eşlenmemiş her AppException için son durak. */
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleApp(AppException ex) {
        return build(ex, HttpStatus.INTERNAL_SERVER_ERROR, true);
    }

    // --- Bean validation (@Valid) ---
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return validationFailed(detail);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBindException(BindException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return validationFailed(detail);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        String detail = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining("; "));
        return validationFailed(detail);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Invalid request argument: {}", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(
                "error.generic.invalid.request", "INVALID_REQUEST", 400));
    }

    // --- DB çakışması ---
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(Exception ex) {
        log.warn("Data conflict: {}", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(
                "error.generic.conflict", "RESOURCE_CONFLICT", 409));
    }

    // --- DB hatası: detay SADECE log'a ---
    @ExceptionHandler({SQLException.class, DataAccessException.class})
    public ResponseEntity<ApiResponse<Void>> handleDatabase(Exception ex) {
        log.error("Database error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(500).body(ApiResponse.error(
                "error.generic.database", "DATABASE_ERROR", 500));
    }

    // --- Son durak: hiçbir hata çıplak kalmasın ---
    // --- Bilinmeyen path: catch-all'a dusup 500 olmamali, 404 donmeli ---
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNotFound(Exception ex) {
        log.warn("No handler for request: {}", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(
                "error.resource.notfound", "NOT_FOUND", 404));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex, HttpServletRequest request) {
        if (isClientAbort(ex)) {
            log.warn("Client disconnected during response write.");
            return ResponseEntity.noContent().build();
        }
        log.error("Unhandled error at {}", request.getRequestURI(), ex);
        return ResponseEntity.status(500).body(ApiResponse.error(
                "error.generic.unexpected", "INTERNAL_ERROR", 500));
    }

    // ---------------- yardımcılar ----------------

    private ResponseEntity<ApiResponse<Void>> build(AppException ex, HttpStatus status, boolean serverFault) {
        if (serverFault) {
            log.error("{} -> {} key={}", ex.getClass().getSimpleName(), status, ex.getMessageKey(), ex);
        } else {
            log.warn("{} -> {} key={} args={}", ex.getClass().getSimpleName(), status,
                    ex.getMessageKey(), java.util.Arrays.toString(ex.getArgs()));
        }
        return ResponseEntity.status(status).body(ApiResponse.error(ex.getMessageKey(), ex.getErrorCode(), status.value(), ex.getArgs()));
    }

    private ResponseEntity<ApiResponse<Void>> validationFailed(String detail) {
        log.warn("Validation failed: {}", detail);
        return ResponseEntity.badRequest().body(ApiResponse.error(
                "error.validation.failed", "VALIDATION_FAILED", 400));
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
