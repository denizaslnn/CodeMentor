package com.codementor.codeservice.exception;

/**
 * Thrown when a refresh token is missing, unknown, revoked, expired or cannot
 * be rotated (e.g. concurrent refresh already consumed it).
 */
public class RefreshTokenException extends AppException {
    public RefreshTokenException(String messageKey, String errorCode) {
        super(messageKey, errorCode);
    }
}