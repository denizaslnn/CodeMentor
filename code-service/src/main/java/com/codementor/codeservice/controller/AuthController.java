package com.codementor.codeservice.controller;

import com.codementor.codeservice.dto.ApiResponse;
import com.codementor.codeservice.dto.AuthResponse;
import com.codementor.codeservice.dto.AuthResult;
import com.codementor.codeservice.dto.LoginRequest;
import com.codementor.codeservice.dto.RegisterRequest;
import com.codementor.codeservice.dto.RegisterResponse;
import com.codementor.codeservice.service.AuthService;
import com.codementor.codeservice.service.RefreshTokenCookieService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookieService cookieService;

    public AuthController(AuthService authService, RefreshTokenCookieService cookieService) {
        this.authService = authService;
        this.cookieService = cookieService;
    }

    @PostMapping("/register")
    public ApiResponse<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        var user = authService.register(request);
        return ApiResponse.success(new RegisterResponse(user.getUsername(), user.getRole()));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletResponse response) {
        AuthResult result = authService.login(request);
        setRefreshTokenCookie(response, result);
        return ApiResponse.success(new AuthResponse(result.accessToken(), result.expiresInSeconds()));
    }

    /**
     * Rotates the refresh token (read from the HttpOnly cookie) and returns a
     * new access token. Deliberately does NOT require an Authorization header:
     * the access token may already be expired when this call happens.
     */
    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(HttpServletRequest request,
                                                HttpServletResponse response) {
        String refreshToken = getRefreshTokenCookie(request);
        AuthResult result = authService.refresh(refreshToken);
        setRefreshTokenCookie(response, result);
        return ApiResponse.success(new AuthResponse(result.accessToken(), result.expiresInSeconds()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request,
                                       HttpServletResponse response) {
        authService.logout(getRefreshTokenCookie(request));
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieService.createClearRefreshTokenCookie().toString());
        return ApiResponse.success(null);
    }

    private void setRefreshTokenCookie(HttpServletResponse response, AuthResult result) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieService.createRefreshTokenCookie(
                        result.refreshToken(), result.refreshTokenExpirationSeconds()).toString());
    }

    private String getRefreshTokenCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookieService.getCookieName().equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}