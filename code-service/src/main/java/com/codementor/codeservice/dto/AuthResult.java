package com.codementor.codeservice.dto;

/**
 * Internal result of a login/refresh operation. The raw refresh token is
 * returned to the controller layer only, which writes it into an HttpOnly
 * cookie; it is never put in a response body.
 */
public record AuthResult(
        String accessToken,
        long expiresInSeconds,
        String refreshToken,
        long refreshTokenExpirationSeconds
) {
}