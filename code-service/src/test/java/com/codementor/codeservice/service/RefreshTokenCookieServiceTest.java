package com.codementor.codeservice.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class RefreshTokenCookieServiceTest {

    @Test
    void refreshTokenCookie_isHttpOnlySecureLaxAndExpiresInMaxAge() {
        RefreshTokenCookieService service = new RefreshTokenCookieService("refresh_token", true, "Lax", "/");

        ResponseCookie cookie = service.createRefreshTokenCookie("raw-token", 604_800);

        assertTrue(cookie.isHttpOnly());
        assertTrue(cookie.isSecure());
        assertEquals("Lax", cookie.getSameSite());
        assertEquals("/", cookie.getPath());
        assertEquals(Duration.ofSeconds(604_800), cookie.getMaxAge());
    }

    @Test
    void clearCookie_expiresImmediately() {
        RefreshTokenCookieService service = new RefreshTokenCookieService("refresh_token", true, "Lax", "/");

        ResponseCookie cookie = service.createClearRefreshTokenCookie();

        assertTrue(cookie.isHttpOnly());
        assertEquals(0L, cookie.getMaxAge().getSeconds());
    }

    @Test
    void unsupportedSameSite_fallsBackToLax() {
        RefreshTokenCookieService service = new RefreshTokenCookieService("refresh_token", true, "None", "/");
        assertEquals("Lax", service.createRefreshTokenCookie("x", 10).getSameSite());
    }
}