package com.codementor.codeservice.service;

import com.codementor.codeservice.entity.RefreshToken;
import com.codementor.codeservice.entity.User;
import com.codementor.codeservice.exception.RefreshTokenException;
import com.codementor.codeservice.repository.RefreshTokenRepository;
import com.github.f4b6a3.uuid.UuidCreator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Creates, hashes, stores and rotates refresh tokens.
 * <p>
 * A raw refresh token is a 256-bit cryptographically-secure random value that
 * is shown to the client exactly once (in an HttpOnly cookie). Only its
 * SHA-256 hash is persisted - the raw token is never stored or logged.
 * <p>
 * Rotation is race-safe: the CAS-style conditional update in
 * {@link RefreshTokenRepository#rotateIfActive} guarantees that only one
 * concurrent request can consume a token.
 */
@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTE_LENGTH = 32; // 256-bit
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final long refreshTokenValidityMs;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               @Value("${jwt.refresh-token-expiration:604800000}") long refreshTokenValidityMs) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenValidityMs = refreshTokenValidityMs;
    }

    /** Generates a new random refresh token (base64url, no padding). */
    public String generateRawRefreshToken() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 hex digest of the raw token - the only representation stored in DB. */
    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest unavailable", e);
        }
    }

    @Transactional
    public String createRefreshToken(User user) {
        String rawToken = generateRawRefreshToken();
        refreshTokenRepository.save(buildEntity(user, rawToken));
        return rawToken;
    }

    /**
     * Claims the token for rotation. Returns the new raw refresh token on
     * success (to be written into the cookie) or throws
     * {@link RefreshTokenException} if the token was already consumed
     * (concurrent rotation), revoked or expired.
     */
    @Transactional
    public String rotate(RefreshToken existing) {
        String newRawToken = generateRawRefreshToken();
        LocalDateTime now = LocalDateTime.now();
        RefreshToken newToken = buildEntity(existing.getUser(), newRawToken);

        int updated = refreshTokenRepository.rotateIfActive(
                existing.getTokenHash(), now, newToken.getId(), now);
        if (updated != 1) {
            throw new RefreshTokenException("error.auth.refreshtoken.invalid", "REFRESH_TOKEN_CONSUMED");
        }
        refreshTokenRepository.save(newToken);
        return newRawToken;
    }

    @Transactional(readOnly = true)
    public Optional<RefreshToken> findByHash(String tokenHash) {
        return refreshTokenRepository.findByTokenHash(tokenHash);
    }

    /** Idempotent revocation used by logout. */
    @Transactional
    public void revokeActiveByHash(String tokenHash) {
        refreshTokenRepository.revokeActiveByHash(tokenHash, LocalDateTime.now());
    }

    /** Revokes every still-active token of a user (reuse-detection countermeasure).
     * <p>
     * Runs in its own (REQUIRES_NEW) transaction: the caller rolls back its
     * transaction when it rejects the reused token, and without this
     * propagation the family revocation would be rolled back too - leaving the
     * attacker's stolen token family partially alive.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAllActiveByUser(String userId) {
        refreshTokenRepository.revokeAllActiveByUser(userId, LocalDateTime.now());
    }

    public long getRefreshTokenValiditySeconds() {
        return Math.max(1, refreshTokenValidityMs / 1000);
    }

    private RefreshToken buildEntity(User user, String rawToken) {
        LocalDateTime now = LocalDateTime.now();
        RefreshToken entity = new RefreshToken();
        entity.setId(UuidCreator.getTimeOrdered().toString());
        entity.setUser(user);
        entity.setTokenHash(hashToken(rawToken));
        entity.setExpiresAt(now.plusNanos(refreshTokenValidityMs * 1_000_000L));
        entity.setCreatedAt(now);
        return entity;
    }
}