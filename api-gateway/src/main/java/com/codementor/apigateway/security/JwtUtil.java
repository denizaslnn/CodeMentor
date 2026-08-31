package com.codementor.apigateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;

/**
 * Verifies stateless access-token JWTs on behalf of the api-gateway.
 * <p>
 * Security posture (production hardening):
 * <ul>
 *   <li>Secret must decode to an <b>at least 256-bit</b> (>=32 byte) HMAC key.</li>
 *   <li>The signature algorithm is pinned to {@link #EXPECTED_ALGORITHM} (HS256) to
 *       defeat <code>alg: none</code> and asymmetric/algorithm-confusion attacks.</li>
 *   <li>The {@code iss} (issuer) and {@code aud} (audience) claims are required and
 *       checked against the configured values, so a token minted for a different
 *       consumer cannot be replayed against this gateway.</li>
 *   <li> {@code sub}, {@code userId}, {@code username} and {@code role} claims are
 *       mandatory; tokens missing any of them are rejected.</li>
 * </ul>
 * The secret is shared with code-service's {@code JwtTokenProvider} and is supplied
 * only via the {@code JWT_SECRET} environment variable.
 */
@Component
@Slf4j
public class JwtUtil {

    static final String EXPECTED_ALGORITHM = "HS256";
    static final int MIN_HMAC_KEY_BYTES = 32; // 256-bit minimum for HS256

    private final String base64Secret;
    private final long accessTokenValidityMs;
    private final String expectedIssuer;
    private final String expectedAudience;
    private SecretKey key;

        private static final String DEFAULT_ISSUER = "codementor";
    private static final String DEFAULT_AUDIENCE = "api-gateway";
    private static final long DEFAULT_ACCESS_TOKEN_VALIDITY_MS = 15 * 60 * 1000L;

    /** Convenience constructor with the default issuer/audience (tests, local tooling). */
    public JwtUtil(String base64Secret, long accessTokenValidityMs) {
        this(base64Secret, accessTokenValidityMs, DEFAULT_ISSUER, DEFAULT_AUDIENCE);
    }

    /**
     * Constructor Spring injects. Explicitly annotated because this class exposes
     * more than one constructor (the 2-arg one is a convenience used by tests),
     * and Spring cannot pick a candidate on its own in that case.
     */
    @Autowired
    public JwtUtil(@Value("${jwt.secret}") String base64Secret,
                   @Value("${jwt.access-token-expiration:900000}") long accessTokenValidityMs,
                   @Value("${jwt.issuer:" + DEFAULT_ISSUER + "}") String expectedIssuer,
                   @Value("${jwt.audience:" + DEFAULT_AUDIENCE + "}") String expectedAudience) {
        this.base64Secret = base64Secret;
        this.accessTokenValidityMs = accessTokenValidityMs;
        this.expectedIssuer = expectedIssuer;
        this.expectedAudience = expectedAudience;
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
     * Validates the signature, expiration, algorithm, issuer and audience, then
     * returns the parsed JWS claims. Throws {@link JwtException} (or subclass) on
     * any failure so callers can uniformly return 401.
     */
    public Jws<Claims> validateAndParse(String token) throws JwtException {
        try {
            Jws<Claims> jws = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token);

            // Algorithm pinning: prevents alg=none / algorithm-confusion.
            String alg = jws.getHeader().getAlgorithm();
            if (!EXPECTED_ALGORITHM.equals(alg)) {
                throw new JwtException("Unexpected JWT algorithm: " + alg
                        + " (expected " + EXPECTED_ALGORITHM + ")");
            }
            // Issuer pinning.
            String iss = jws.getBody().getIssuer();
            if (iss == null || !iss.equals(expectedIssuer)) {
                throw new JwtException("Invalid JWT issuer: " + iss);
            }
            // Audience pinning.
            String aud = jws.getBody().getAudience();
            if (aud == null || !aud.equals(expectedAudience)) {
                throw new JwtException("Invalid JWT audience: " + aud);
            }
            return jws;
        } catch (SignatureException e) {
            // Bad signature (or tampered token)
            throw new JwtException("JWT signature verification failed.", e);
        }
    }

    public String getSubject(Jws<Claims> jws) {
        return jws.getBody().getSubject();
    }

    public String getClaimAsString(Jws<Claims> jws, String claimName) {
        Object v = jws.getBody().get(claimName);
        return v != null ? v.toString() : null;
    }

    /**
     * Role of the token subject: preferred from the {@code role} claim, with a
     * fallback to the first entry of the {@code roles} claim.
     */
    public String getRole(Jws<Claims> jws) {
        String role = getClaimAsString(jws, "role");
        if (role != null && !role.isBlank()) {
            return role;
        }
        Object roles = jws.getBody().get("roles");
        if (roles instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            return first != null ? first.toString() : null;
        }
        return null;
    }

    /**
     * Checks the claims that the auth flow guarantees on every access token:
     * subject/userId, username and role. Tokens missing any of them are
     * rejected by the gateway even if the signature is valid.
     */
    public boolean hasRequiredClaims(Jws<Claims> jws) {
        String subject = getSubject(jws);
        String userId = getClaimAsString(jws, "userId");
        String username = getClaimAsString(jws, "username");
        String role = getRole(jws);
        return subject != null && !subject.isBlank()
                && userId != null && !userId.isBlank()
                && username != null && !username.isBlank()
                && role != null && !role.isBlank();
    }

    public long getAccessTokenValidityMs() {
        return accessTokenValidityMs;
    }

        /**
     * Generates a signed HS256 JWT carrying sub/userId, username, role, the
     * expected {@code iss} and {@code aud} claims, plus an explicit roles list.
     * Never includes passwords or sensitive data.
     */
    public String generateToken(String userId, String username, String role) {
        return generateToken(userId, username, role, accessTokenValidityMs);
    }

    public String generateToken(String userId, String username, String role, long validityMilliseconds) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + validityMilliseconds);
        return Jwts.builder()
                .setSubject(userId)
                .setIssuer(expectedIssuer)
                .setAudience(expectedAudience)
                .claim("userId", userId)
                .claim("username", username)
                .claim("role", role)
                .claim("roles", List.of(role))
                .setIssuedAt(now)
                .setExpiration(exp)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Backwards-compatible convenience: token without username/role claims
     * (used only by tests/local tooling).
     */
    @Deprecated(forRemoval = true)
    public String generateToken(String userId) {
        return generateToken(userId, DEFAULT_ACCESS_TOKEN_VALIDITY_MS);
    }

    /**
     * Backwards-compatible convenience: token without username/role claims
     * (used only by tests/local tooling).
     */
    @Deprecated(forRemoval = true)
    public String generateToken(String userId, long validityMilliseconds) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + validityMilliseconds);
        return Jwts.builder()
                .setSubject(userId)
                .setIssuer(expectedIssuer)
                .setAudience(expectedAudience)
                .claim("userId", userId)
                .setIssuedAt(now)
                .setExpiration(exp)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
}
