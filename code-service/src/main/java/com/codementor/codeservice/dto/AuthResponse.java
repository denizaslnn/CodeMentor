package com.codementor.codeservice.dto;

/**
 * Login / refresh response body. Contains only the short-lived access token;
 * the refresh token travels exclusively in an HttpOnly cookie.
 */
public record AuthResponse(
        String accessToken,
        long expiresIn
) {
}