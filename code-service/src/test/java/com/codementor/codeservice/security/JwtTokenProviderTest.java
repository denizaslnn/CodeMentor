package com.codementor.codeservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private static final String SECRET_B64 = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void generatedAccessToken_containsRequiredClaims_andNoPassword() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET_B64, 900_000L, "codementor", "api-gateway");
        provider.init();

        String token = provider.generateAccessToken("u-1", "alice", "USER");

        Jws<Claims> jws = Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET_B64)))
                .build()
                .parseClaimsJws(token);

        assertEquals("u-1", jws.getBody().getSubject());
        assertEquals("u-1", jws.getBody().get("userId", String.class));
        assertEquals("alice", jws.getBody().get("username", String.class));
        assertEquals("USER", jws.getBody().get("role", String.class));
        assertEquals(List.of("USER"), jws.getBody().get("roles", List.class));
        // Tokens are scoped to the gateway audience so they cannot be replayed elsewhere.
        assertEquals("codementor", jws.getBody().getIssuer());
        assertEquals("api-gateway", jws.getBody().getAudience());

        long ttl = jws.getBody().getExpiration().getTime() - jws.getBody().getIssuedAt().getTime();
        assertTrue(ttl <= 900_100 && ttl >= 899_900, "ttl=" + ttl);

        assertFalse(jws.getBody().containsKey("password"));
    }

    @Test
    void missingSecret_failsFastOnInit() {
        assertThrows(IllegalStateException.class,
                () -> new JwtTokenProvider("   ", 900_000L, "codementor", "api-gateway").init());
    }

    @Test
    void weakSecret_failsFastOnInit() {
        String weakSecret = Base64.getEncoder().encodeToString(new byte[16]); // 128-bit < 256-bit
        assertThrows(IllegalStateException.class,
                () -> new JwtTokenProvider(weakSecret, 900_000L, "codementor", "api-gateway").init());
    }

    @Test
    void expiration_isConfigurable() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET_B64, 60_000L, "codementor", "api-gateway");
        provider.init();

        String token = provider.generateAccessToken("u-1", "alice", "USER");
        Jws<Claims> jws = Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET_B64)))
                .build()
                .parseClaimsJws(token);

        long ttl = jws.getBody().getExpiration().getTime() - jws.getBody().getIssuedAt().getTime();
        assertTrue(ttl <= 60_100 && ttl >= 59_900, "ttl=" + ttl);
        assertEquals(60_000L, provider.getAccessTokenValidityMs());
    }
}
