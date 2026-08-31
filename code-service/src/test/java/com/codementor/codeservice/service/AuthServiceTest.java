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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String ACCESS_TOKEN = "access-token";
    private static final String NEW_REFRESH_TOKEN = "new-refresh-token";

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthService authService;

    private User user(String id, String username) {
        return User.builder()
                .id(id)
                .username(username)
                .passwordHash("hash")
                .role("USER")
                .enabled(true)
                .build();
    }

    private RefreshToken activeToken(String userId, String username) {
        RefreshToken rt = new RefreshToken();
        rt.setId("rt-1");
        rt.setUser(user(userId, username));
        rt.setTokenHash("hash-of-raw");
        rt.setCreatedAt(LocalDateTime.now().minusDays(1));
        rt.setExpiresAt(LocalDateTime.now().plusDays(7));
        return rt;
    }

    // Doğru username/password -> login başarılı (AT + RT üretilir)
    @Test
    void login_withValidCredentials_returnsSession() {
        User user = user("u-1", "alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
        when(refreshTokenService.createRefreshToken(user)).thenReturn("raw-refresh");
        when(jwtTokenProvider.generateAccessToken("u-1", "alice", "USER")).thenReturn(ACCESS_TOKEN);
        when(jwtTokenProvider.getAccessTokenValidityMs()).thenReturn(900_000L);
        when(refreshTokenService.getRefreshTokenValiditySeconds()).thenReturn(604_800L);

        AuthResult result = authService.login(new LoginRequest("alice", "secret"));

        assertEquals(ACCESS_TOKEN, result.accessToken());
        assertEquals(900, result.expiresInSeconds());
        assertEquals("raw-refresh", result.refreshToken());
        assertEquals(604_800, result.refreshTokenExpirationSeconds());
    }

    // Yanlış password -> 401
    @Test
    void login_withWrongPassword_throwsInvalidCredentials() {
        User user = user("u-1", "alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest("alice", "wrong")));
        verify(refreshTokenService, never()).createRefreshToken(any());
    }

    // Olmayan kullanıcı -> 401
    @Test
    void login_withUnknownUser_throwsInvalidCredentials() {
        when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest("nobody", "whatever")));
    }

    // Kayıt başarılı -> password yalnızca hash'lenerek saklanır, default role USER
    @Test
    void register_createsUserWithHashedPassword() {
        RegisterRequest request = new RegisterRequest("alice", "secret123", null);
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("$2a$12$fakehash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User created = authService.register(request);

        assertEquals("alice", created.getUsername());
        assertEquals("$2a$12$fakehash", created.getPasswordHash());
        assertEquals("USER", created.getRole());
        assertTrue(created.isEnabled());
        verify(passwordEncoder).encode("secret123");
    }

    // Aynı username ile tekrar kayıt -> 409, DB'ye kayıt yapılmaz
    @Test
    void register_withDuplicateUsername_throws() {
        RegisterRequest request = new RegisterRequest("alice", "secret123", null);
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThrows(UsernameAlreadyExistsException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any());
    }

    // Geçerli Refresh Token -> yeni Access Token + rotation
    @Test
    void refresh_withValidToken_rotatesAndReturnsNewAccessToken() {
        RefreshToken stored = activeToken("u-1", "alice");
        when(refreshTokenService.hashToken("raw-token")).thenReturn("hash-of-raw");
        when(refreshTokenService.findByHash("hash-of-raw")).thenReturn(Optional.of(stored));
        when(refreshTokenService.rotate(stored)).thenReturn(NEW_REFRESH_TOKEN);
        when(jwtTokenProvider.generateAccessToken("u-1", "alice", "USER")).thenReturn(ACCESS_TOKEN);
        when(jwtTokenProvider.getAccessTokenValidityMs()).thenReturn(900_000L);
        when(refreshTokenService.getRefreshTokenValiditySeconds()).thenReturn(604_800L);

        AuthResult result = authService.refresh("raw-token");

        assertEquals(ACCESS_TOKEN, result.accessToken());
        assertEquals(NEW_REFRESH_TOKEN, result.refreshToken());
        verify(refreshTokenService).rotate(stored);
    }

    // Expired Refresh Token -> 401
    @Test
    void refresh_withExpiredToken_throws() {
        RefreshToken stored = activeToken("u-1", "alice");
        stored.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(refreshTokenService.hashToken("raw")).thenReturn("h");
        when(refreshTokenService.findByHash("h")).thenReturn(Optional.of(stored));

        assertThrows(RefreshTokenException.class, () -> authService.refresh("raw"));
        verify(refreshTokenService, never()).rotate(any());
    }

    // Revoked Refresh Token -> 401 + reuse detection (aile revoke)
    @Test
    void refresh_withRevokedToken_throws_andRevokesFamily() {
        RefreshToken stored = activeToken("u-1", "alice");
        stored.setRevokedAt(LocalDateTime.now().minusMinutes(1));
        when(refreshTokenService.hashToken("raw")).thenReturn("h");
        when(refreshTokenService.findByHash("h")).thenReturn(Optional.of(stored));

        assertThrows(RefreshTokenException.class, () -> authService.refresh("raw"));
        verify(refreshTokenService).revokeAllActiveByUser("u-1");
        verify(refreshTokenService, never()).rotate(any());
    }

    // Bilinmeyen Refresh Token -> 401
    @Test
    void refresh_withUnknownToken_throws() {
        when(refreshTokenService.hashToken("raw")).thenReturn("h");
        when(refreshTokenService.findByHash("h")).thenReturn(Optional.empty());

        assertThrows(RefreshTokenException.class, () -> authService.refresh("raw"));
    }

    // Refresh Token eksik -> 401
    @Test
    void refresh_withoutToken_throws() {
        assertThrows(RefreshTokenException.class, () -> authService.refresh(null));
        assertThrows(RefreshTokenException.class, () -> authService.refresh("   "));
    }

    // Eski Refresh Token tekrar kullanılamıyor (rotation sonrası revoked)
    @Test
    void oldRefreshToken_cannotBeReused() {
        RefreshToken stored = activeToken("u-1", "alice");
        stored.setRevokedAt(LocalDateTime.now());
        when(refreshTokenService.hashToken("old-token")).thenReturn("old-hash");
        when(refreshTokenService.findByHash("old-hash")).thenReturn(Optional.of(stored));

        assertThrows(RefreshTokenException.class, () -> authService.refresh("old-token"));
        verify(refreshTokenService, never()).rotate(any());
    }

    // Logout token olmadan da tamamlanır (idempotent)
    @Test
    void logout_withoutToken_isNoop() {
        assertDoesNotThrow(() -> authService.logout(null));
        verifyNoInteractions(refreshTokenService);
    }

    // Logout ilgili refresh token'ı revoke eder; sonraki refresh 401'e düşer
    @Test
    void logout_revokesPresentedToken() {
        when(refreshTokenService.hashToken("raw-token")).thenReturn("hash-of-raw");

        authService.logout("raw-token");

        verify(refreshTokenService).revokeActiveByHash("hash-of-raw");
    }
}