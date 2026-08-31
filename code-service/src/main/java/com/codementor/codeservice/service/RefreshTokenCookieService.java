package com.codementor.codeservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Builds the refresh-token cookies.
 * <p>
 * The cookie is HttpOnly (never readable by JavaScript), {@code Secure} when
 * configured (production default {@code true}) and SameSite=Lax, which
 * mitigates CSRF without blocking legitimate same-site navigation flows.
 */
@Service
public class RefreshTokenCookieService {

    private static final String SAME_SITE_LAX = "Lax";
    private static final String SAME_SITE_STRICT = "Strict";

    private final String cookieName;
    private final boolean secure;
    private final String sameSite;
    private final String cookiePath;

    public RefreshTokenCookieService(@Value("${jwt.refresh-cookie.name:refresh_token}") String cookieName,
                                     @Value("${jwt.refresh-cookie.secure:true}") boolean secure,
                                     @Value("${jwt.refresh-cookie.same-site:Lax}") String sameSite,
                                     @Value("${jwt.refresh-cookie.path:/api/v1/auth}") String cookiePath) {
        this.cookieName = cookieName;
        this.secure = secure;
        this.sameSite = sanitizeSameSite(sameSite);
        this.cookiePath = cookiePath;
    }

    public ResponseCookie createRefreshTokenCookie(String rawToken, long maxAgeSeconds) {
        return buildCookie(rawToken, maxAgeSeconds);
    }

    /** Sets a max-age=0 cookie so the browser deletes the refresh token. */
    public ResponseCookie createClearRefreshTokenCookie() {
        return buildCookie("", 0L);
    }

    public String getCookieName() {
        return cookieName;
    }

    private ResponseCookie buildCookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(cookieName, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(cookiePath)
                .maxAge(maxAgeSeconds)
                .build();
    }

    /**
     * Normalizes the configured SameSite value to one of {@code Lax}/{@code
     * Strict}, falling back to {@code Lax} for anything unsupported. Note:
     * {@code None} is deliberately rejected because it would require the
     * {@code Secure} flag and weakens CSRF protection.
     */
    private static String sanitizeSameSite(String value) {
        if (value == null || value.isBlank()) {
            return SAME_SITE_LAX;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (SAME_SITE_STRICT.toUpperCase(Locale.ROOT).equals(normalized)) {
            return SAME_SITE_STRICT;
        }
        return SAME_SITE_LAX;
    }
}