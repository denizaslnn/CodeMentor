package com.codementor.codeservice.service;

import com.codementor.codeservice.dto.AuthResult;
import com.codementor.codeservice.dto.LoginRequest;
import com.codementor.codeservice.dto.RegisterRequest;
import com.codementor.codeservice.entity.RefreshToken;
import com.codementor.codeservice.entity.User;
import com.codementor.codeservice.exception.InvalidCredentialsException;
import com.codementor.codeservice.exception.RefreshTokenException;
import com.codementor.codeservice.exception.UsernameAlreadyExistsException;
import com.codementor.codeservice.repository.UserRepository;
import com.codementor.codeservice.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class AuthService {

    private static final String DEFAULT_ROLE = "USER";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public User register(RegisterRequest request) {
        String username = request.username().trim();
        if (userRepository.existsByUsername(username)) {
            throw new UsernameAlreadyExistsException(username);
        }

        String role = normalizeRole(request.role());
        User user = User.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(role)
                .enabled(true)
                .build();
        return userRepository.save(user);
    }

    /**
     * Authenticates the user and issues a session: a stateless access token
     * (returned in the body) and a refresh token (returned raw to be written
     * into an HttpOnly cookie by the controller).
     */
    @Transactional
    public AuthResult login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username().trim())
                .orElseThrow(InvalidCredentialsException::new);

        if (!user.isEnabled() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            // Same generic message as "user not found" -> reduced user enumeration risk.
            throw new InvalidCredentialsException();
        }

        return issueSession(user);
    }

    /**
     * Refresh-token rotation (new refresh token is stored hashed, old one is
     * revoked atomically) plus a freshly signed access token.
     */
    @Transactional
    public AuthResult refresh(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new RefreshTokenException("error.auth.refreshtoken.missing", "REFRESH_TOKEN_MISSING");
        }

        String tokenHash = refreshTokenService.hashToken(rawToken);
        RefreshToken stored = refreshTokenService.findByHash(tokenHash)
                .orElseThrow(() -> new RefreshTokenException("error.auth.refreshtoken.invalid", "INVALID_REFRESH_TOKEN"));

        if (stored.getRevokedAt() != null) {
            // Reuse detection: a revoked token that is presented again is a strong
            // sign of token theft -> revoke the whole token family of the user.
            refreshTokenService.revokeAllActiveByUser(stored.getUser().getId());
            throw new RefreshTokenException("error.auth.refreshtoken.invalid", "REFRESH_TOKEN_REVOKED");
        }

        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RefreshTokenException("error.auth.refreshtoken.expired", "REFRESH_TOKEN_EXPIRED");
        }

        String newRefreshToken = refreshTokenService.rotate(stored);
        User user = stored.getUser();

        return new AuthResult(
                jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole()),
                jwtTokenProvider.getAccessTokenValidityMs() / 1000,
                newRefreshToken,
                refreshTokenService.getRefreshTokenValiditySeconds());
    }

    /**
     * Idempotent logout: the presented refresh token (if any) is revoked and
     * the cookie is cleared by the controller. A missing or unknown token is
     * not an error.
     */
    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenService.revokeActiveByHash(refreshTokenService.hashToken(rawToken));
    }

    private AuthResult issueSession(User user) {
        String refreshToken = refreshTokenService.createRefreshToken(user);
        return new AuthResult(
                jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole()),
                jwtTokenProvider.getAccessTokenValidityMs() / 1000,
                refreshToken,
                refreshTokenService.getRefreshTokenValiditySeconds());
    }

    private static String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return DEFAULT_ROLE;
        }
        return role.trim().toUpperCase(Locale.ROOT);
    }
}