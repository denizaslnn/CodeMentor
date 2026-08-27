package com.codementor.apigateway.filter;

import com.codementor.apigateway.security.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class JwtAuthenticationFilterTest {

    private static final String SECRET_B64 = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void validToken_isAccepted_andAddsXUserIdHeader() {
        JwtUtil jwtUtil = new JwtUtil(SECRET_B64);
        jwtUtil.init();
        UnauthorizedResponseWriter unauthorizedResponseWriter = new UnauthorizedResponseWriter();

        String token = buildToken(jwtUtil, "user-42", new Date(System.currentTimeMillis() + 60_000));

        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/analyze")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("{\"sourceCode\":\"test\"}");

        ServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        GatewayFilterChain chain = mockedExchange -> {
            chainInvoked.set(true);
            assertEquals("user-42", mockedExchange.getRequest().getHeaders().getFirst("X-User-Id"));
            return Mono.empty();
        };

        new JwtAuthenticationFilter(jwtUtil, unauthorizedResponseWriter).apply(new JwtAuthenticationFilter.Config())
                .filter(exchange, chain)
                .block();

        assertTrue(chainInvoked.get());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    void invalidToken_returns401_andStopsChain() {
        JwtUtil jwtUtil = new JwtUtil(SECRET_B64);
        jwtUtil.init();
        UnauthorizedResponseWriter unauthorizedResponseWriter = new UnauthorizedResponseWriter();

        String invalidToken = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyLTQyIn0.invalid";

        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/analyze")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + invalidToken)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("{\"sourceCode\":\"test\"}");

        ServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        GatewayFilterChain chain = mockedExchange -> {
            chainInvoked.set(true);
            return Mono.empty();
        };

        new JwtAuthenticationFilter(jwtUtil, unauthorizedResponseWriter).apply(new JwtAuthenticationFilter.Config())
                .filter(exchange, chain)
                .block();

        assertFalse(chainInvoked.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void expiredToken_isRejected() {
        JwtUtil jwtUtil = new JwtUtil(SECRET_B64);
        jwtUtil.init();
        UnauthorizedResponseWriter unauthorizedResponseWriter = new UnauthorizedResponseWriter();

        String expiredToken = buildToken(jwtUtil, "expired-user", new Date(System.currentTimeMillis() - 60_000));

        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/analyze")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
                .body("{}");

        ServerWebExchange exchange = MockServerWebExchange.from(request);

        new JwtAuthenticationFilter(jwtUtil, unauthorizedResponseWriter).apply(new JwtAuthenticationFilter.Config())
                .filter(exchange, mockedExchange -> Mono.empty())
                .block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    private static String buildToken(JwtUtil jwtUtil, String userId, Date expiration) {
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET_B64));
        Claims claims = Jwts.claims().setSubject(userId);
        claims.put("userId", userId);

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(new Date())
                .setExpiration(expiration)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
}
