package com.codementor.apigateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtil {

    private final String base64Secret;
    private SecretKey key;

    public JwtUtil(@Value("${jwt.secret}") String base64Secret) {
        this.base64Secret = base64Secret;
    }

    @PostConstruct
    public void init() {
        byte[] keyBytes = Decoders.BASE64.decode(base64Secret);
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Validates the token signature and expiration. Returns parsed JWS claims on success or throws JwtException on failure.
     */
    public Jws<Claims> validateAndParse(String token) throws JwtException {
        // Will throw JwtException subclasses on invalid signature/expired token/etc.
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token);
    }

    public String getSubject(Jws<Claims> jws) {
        return jws.getBody().getSubject();
    }

    public String getClaimAsString(Jws<Claims> jws, String claimName) {
        Object v = jws.getBody().get(claimName);
        return v != null ? v.toString() : null;
    }

    /**
     * Generates a signed HS256 JWT for a given user id with a default time-to-live
     * of 15 minutes. Convenience overload of
     * {@link #generateToken(String, long)}.
     */
    public String generateToken(String userId) {
        return generateToken(userId, 15 * 60 * 1000L);
    }


    public String generateToken(String userId, long validityMilliseconds) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + validityMilliseconds);
        return Jwts.builder()
                .setSubject(userId)
                .claim("userId", userId)
                .setIssuedAt(now)
                .setExpiration(exp)
                .signWith(key) // HS algorithm derived from the configured secret key
                .compact();
    }
}
