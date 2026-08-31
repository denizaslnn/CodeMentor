package com.codementor.apigateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private static final String SECRET_B64 = Base64.getEncoder().encodeToString(new byte[32]);

    private JwtUtil jwtUtil() {
        JwtUtil util = new JwtUtil(SECRET_B64, 900_000L);
        util.init();
        return util;
    }

    private static String b64url(String s) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void validToken_validatesAndExposesClaims() {
        JwtUtil util = jwtUtil();
        String token = util.generateToken("u-1", "alice", "USER");

        Jws<Claims> jws = util.validateAndParse(token);

        assertEquals("u-1", jws.getBody().getSubject());
        assertEquals("codementor", jws.getBody().getIssuer());
        assertEquals("api-gateway", jws.getBody().getAudience());
        assertEquals("alice", jws.getBody().get("username", String.class));
        assertEquals("USER", jws.getBody().get("role", String.class));
    }

    @Test
    void expiredToken_throwsJwtException() {
        JwtUtil util = jwtUtil();
        String expired = util.generateToken("u-1", "alice", "USER", -60_000L);

        assertThrows(JwtException.class, () -> util.validateAndParse(expired));
    }

    @Test
    void unsignedAlgNoneToken_throwsJwtException() {
        JwtUtil util = jwtUtil();
        String unsigned = b64url("{\"alg\":\"none\"}")
                + "." + b64url("{\"sub\":\"u-1\",\"iss\":\"codementor\",\"aud\":\"api-gateway\"}")
                + ".";

        assertThrows(JwtException.class, () -> util.validateAndParse(unsigned));
    }

    @Test
    void wrongIssuer_throwsJwtException() {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET_B64));
        String token = Jwts.builder()
                .setSubject("u-1")
                .setIssuer("wrong-issuer")
                .setAudience("api-gateway")
                .claim("userId", "u-1")
                .claim("username", "alice")
                .claim("role", "USER")
                .claim("roles", List.of("USER"))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();

        assertThrows(JwtException.class, () -> jwtUtil().validateAndParse(token));
    }

    @Test
    void wrongAudience_throwsJwtException() {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET_B64));
        String token = Jwts.builder()
                .setSubject("u-1")
                .setIssuer("codementor")
                .setAudience("wrong-audience")
                .claim("userId", "u-1")
                .claim("username", "alice")
                .claim("role", "USER")
                .claim("roles", List.of("USER"))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();

        assertThrows(JwtException.class, () -> jwtUtil().validateAndParse(token));
    }

    @Test
    void tamperedSignature_throwsJwtException() {
        JwtUtil util = jwtUtil();
        String token = util.generateToken("u-1", "alice", "USER");
        String[] parts = token.split("\\.");
        String tamperedPayload = parts[1].substring(0, parts[1].length() - 1) + 'X';
        String tampered = parts[0] + "." + tamperedPayload + "." + parts[2];

        assertThrows(JwtException.class, () -> util.validateAndParse(tampered));
    }

    @Test
    void weakSecret_failsFastOnInit() {
        String weakSecret = Base64.getEncoder().encodeToString(new byte[16]); // 128-bit < 256-bit
        JwtUtil util = new JwtUtil(weakSecret, 900_000L);

        assertThrows(IllegalStateException.class, util::init);
    }
}
