package com.codementor.codeservice.service;

import com.codementor.codeservice.entity.RefreshToken;
import com.codementor.codeservice.entity.User;
import com.codementor.codeservice.exception.RefreshTokenException;
import com.codementor.codeservice.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RefreshTokenServiceTest {

    private RefreshTokenRepository repository;
    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        repository = mock(RefreshTokenRepository.class);
        service = new RefreshTokenService(repository, 604_800_000L);
    }

    // Login sonrasi refresh token DB'de hash olarak tutuluyor
    @Test
    void createRefreshToken_persistsHashNotPlaintext() {
        User user = User.builder().id("u-1").build();
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);

        String raw = service.createRefreshToken(user);

        verify(repository).save(captor.capture());
        RefreshToken saved = captor.getValue();
        assertNotEquals(raw, saved.getTokenHash());
        assertEquals(service.hashToken(raw), saved.getTokenHash());
        assertEquals(64, saved.getTokenHash().length());
        assertEquals("u-1", saved.getUser().getId());
        assertNotNull(saved.getExpiresAt());
    }

    @Test
    void refreshToken_isCryptographicallyRandomPerCall() {
        String t1 = service.generateRawRefreshToken();
        String t2 = service.generateRawRefreshToken();
        assertNotEquals(t1, t2);
        // 32 bytes base64url (no padding) -> 43 chars
        assertTrue(t1.length() >= 43);
    }

    @Test
    void hashToken_isSha256Hex() {
        String hash = service.hashToken("raw");
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"));
        assertEquals(hash, service.hashToken("raw"));
    }

    @Test
    void rotate_success_revokesOldAndStoresNewHashed() {
        User user = User.builder().id("u-1").build();
        RefreshToken existing = new RefreshToken();
        existing.setId("rt-old");
        existing.setUser(user);
        existing.setTokenHash("old-hash");
        existing.setExpiresAt(LocalDateTime.now().plusDays(1));
        when(repository.rotateIfActive(any(), any(), any(), any())).thenReturn(1);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        String newRaw = service.rotate(existing);

        verify(repository).rotateIfActive(eq("old-hash"), any(), any(), any());
        verify(repository).save(captor.capture());
        assertEquals(service.hashToken(newRaw), captor.getValue().getTokenHash());
        assertNotEquals(newRaw, captor.getValue().getTokenHash());
        assertEquals("u-1", captor.getValue().getUser().getId());
    }

    // Ayni token ile ikinci eszamanli refresh kazanamaz -> 401
    @Test
    void rotate_whenAlreadyConsumed_throwsAndDoesNotSave() {
        User user = User.builder().id("u-1").build();
        RefreshToken existing = new RefreshToken();
        existing.setId("rt-old");
        existing.setUser(user);
        existing.setTokenHash("old-hash");
        existing.setExpiresAt(LocalDateTime.now().plusDays(1));
        when(repository.rotateIfActive(any(), any(), any(), any())).thenReturn(0);

        assertThrows(RefreshTokenException.class, () -> service.rotate(existing));
        verify(repository, never()).save(any());
    }

    // İki eşzamanlı refresh isteği: CAS-conditional update sayesinde yalnızca
    // BİRİ kazanır; diğeri 401'e düşer ve yeni token kaydetmez.
    @Test
    void concurrentRotation_onlyOneSucceeds_otherThrows() {
        User user = User.builder().id("u-1").build();
        RefreshToken existing = new RefreshToken();
        existing.setId("rt-old");
        existing.setUser(user);
        existing.setTokenHash("old-hash");
        existing.setExpiresAt(LocalDateTime.now().plusDays(1));

        AtomicInteger casAttempts = new AtomicInteger();
        when(repository.rotateIfActive(any(), any(), any(), any()))
                .thenAnswer(inv -> casAttempts.incrementAndGet() == 1 ? 1 : 0);

        // İlk (kazanan) istek: yeni token hash'lenip kaydedilir
        String winnerToken = service.rotate(existing);
        assertNotNull(winnerToken);

        // İkinci (kaybeden) istek: aynı token ile tekrar rotate -> red, yeni kayıt yok
        assertThrows(RefreshTokenException.class, () -> service.rotate(existing));
        verify(repository, times(1)).save(any());
    }

    @Test
    void revokedOrExpiredToken_cannotBeRotated() {
        User user = User.builder().id("u-1").build();
        RefreshToken existing = new RefreshToken();
        existing.setId("rt-old");
        existing.setUser(user);
        existing.setTokenHash("old-hash");
        existing.setRevokedAt(LocalDateTime.now());
        existing.setExpiresAt(LocalDateTime.now().plusDays(1));
        // roteteIfActive simulates the CAS condition; revoked -> 0 rows updated
        when(repository.rotateIfActive(any(), any(), any(), any())).thenReturn(0);

        assertThrows(RefreshTokenException.class, () -> service.rotate(existing));
    }

    @Test
    void revokeActiveByHash_isIdempotent() {
        service.revokeActiveByHash("hash");
        verify(repository).revokeActiveByHash(eq("hash"), any());
    }
}