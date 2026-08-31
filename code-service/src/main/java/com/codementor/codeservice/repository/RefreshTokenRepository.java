package com.codementor.codeservice.repository;

import com.codementor.codeservice.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Atomic "claim" of a refresh token for rotation. The single conditional
     * UPDATE (CAS-style) guarantees that only ONE concurrent refresh request
     * can rotate a given token: concurrent contenders see {@code affected == 0}.
     * An already-revoked or already-expired token can never be rotated.
     *
     * @return 1 if this request won the rotation race, 0 otherwise.
     */
    @Modifying
    @Query("""
            UPDATE RefreshToken rt
               SET rt.revokedAt = :revokedAt,
                   rt.replacedByTokenId = :replacedByTokenId
             WHERE rt.tokenHash = :tokenHash
               AND rt.revokedAt IS NULL
               AND rt.expiresAt > :now
            """)
    int rotateIfActive(@Param("tokenHash") String tokenHash,
                       @Param("revokedAt") LocalDateTime revokedAt,
                       @Param("replacedByTokenId") String replacedByTokenId,
                       @Param("now") LocalDateTime now);

    /**
     * Idempotent revocation used by logout: unknown/expired/already-revoked
     * tokens are simply ignored.
     */
    @Modifying
    @Query("""
            UPDATE RefreshToken rt
               SET rt.revokedAt = :revokedAt
             WHERE rt.tokenHash = :tokenHash
               AND rt.revokedAt IS NULL
            """)
    int revokeActiveByHash(@Param("tokenHash") String tokenHash,
                           @Param("revokedAt") LocalDateTime revokedAt);

    /**
     * Reuse-detection countermeasure: when a revoked token is presented again,
     * all still-active tokens of that user are revoked (token-theft mitigation).
     */
    @Modifying
    @Query("""
            UPDATE RefreshToken rt
               SET rt.revokedAt = :revokedAt
             WHERE rt.user.id = :userId
               AND rt.revokedAt IS NULL
            """)
    int revokeAllActiveByUser(@Param("userId") String userId,
                              @Param("revokedAt") LocalDateTime revokedAt);
}