package com.codementor.codeservice.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;

/**
 * Generates signed access-token JWTs on behalf of the code-service.
 * <p>
 * The signature key is the SAME base64 secret used by the api-gateway's
 * {@code JwtUtil}, so the gateway can verify statelessly without any DB or
 * service-to-service call. Production hardening applied here:
 * <ul>
 *   <li>The secret must decode to an <b>at least 256-bit</b> (>=32 byte) key;</li>
 *   <li>Tokens are signed with HS256 and carry {@code iss}/{@code aud} claims so
 *       they are scoped to the gateway audience and cannot be replayed elsewhere;</li>
 *   <li>Access tokens are stateless: they are never persisted.</li>
 * </ul>
 * The secret is supplied only via the {@code JWT_SECRET} environment variable.
 */
@Component
public class JwtTokenProvider {

    static final String EXPECTED_ALGORITHM = "HS256";
    static final int MIN_HMAC_KEY_BYTES = 32; // 256-bit minimum for HS256
    static final String DEFAULT_ISSUER = "codementor";
    static final String DEFAULT_AUDIENCE = "api-gateway";

    public static final long DEFAULT_ACCESS_TOKEN_VALIDITY_MS = 15 * 60 * 1000L;

    private final String base64Secret;
    private final long accessTokenValidityMs;
    private final String issuer;
    private final String audience;
    private SecretKey key;

    public JwtTokenProvider(@Value("${jwt.secret}") String base64Secret,
                            @Value("${jwt.access-token-expiration:900000}") long accessTokenValidityMs,
                            @Value("${jwt.issuer:codementor}") String issuer,
                            @Value("${jwt.audience:api-gateway}") String audience) {
        this.base64Secret = base64Secret;
        this.accessTokenValidityMs = accessTokenValidityMs;
        this.issuer = issuer;
        this.audience = audience;
    }

    @PostConstruct
    public void init() {
        if (base64Secret == null || base64Secret.isBlank()) {
            throw new IllegalStateException(
                    "jwt.secret is not configured. Set the JWT_SECRET environment variable " +
                            "(base64-encoded, >= 256-bit).");
        }
        byte[] keyBytes = Decoders.BASE64.decode(base64Secret);
        if (keyBytes.length < MIN_HMAC_KEY_BYTES) {
            throw new IllegalStateException("jwt.secret must decode to at least "
                    + MIN_HMAC_KEY_BYTES + " bytes (256-bit) for HS256. Got "
                    + keyBytes.length + " bytes.");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Creates a signed HS256 access token containing the claims required by the
     * api-gateway ({@code iss}, {@code aud}, {@code sub}/{@code userId},
     * {@code username}, {@code role}), plus {@code iat} and {@code exp}.
     * Passwords or other sensitive data are NEVER included.
     */
    public String generateAccessToken(String userId, String username, String role) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + accessTokenValidityMs);
        return Jwts.builder()
                .setSubject(userId)
                .setIssuer(issuer)
                .setAudience(audience)
                .claim("userId", userId)
                .claim("username", username)
                .claim("role", role)
                .claim("roles", List.of(role))
                .setIssuedAt(now)
                .setExpiration(exp)
                .signWith(key)
                .compact();
    }

    public long getAccessTokenValidityMs() {
        return accessTokenValidityMs;
    }
}