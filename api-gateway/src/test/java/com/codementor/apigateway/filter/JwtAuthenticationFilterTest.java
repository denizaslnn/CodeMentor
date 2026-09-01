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
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class JwtAuthenticationFilterTest {

    private static final String SECRET_B64 = Base64.getEncoder().encodeToString(new byte[32]);
    private static final long VALIDITY_MS = 900_000L;

    private JwtUtil jwtUtil() {
        JwtUtil jwtUtil = new JwtUtil(SECRET_B64, VALIDITY_MS);
        jwtUtil.init();
        return jwtUtil;
    }

    private JwtAuthenticationFilter newFilter() {
        return new JwtAuthenticationFilter(jwtUtil(), TestResponseWriter.create());
    }

    @Test
    void validToken_isAccepted_andAddsUserHeaders() {
        JwtUtil jwtUtil = jwtUtil();
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil, TestResponseWriter.create());
        String token = buildToken(jwtUtil, "user-42", "alice", "USER",
                new Date(System.currentTimeMillis() + 60_000));

        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/analyze")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("{\"sourceCode\":\"test\"}");
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        GatewayFilterChain chain = mockedExchange -> {
            chainInvoked.set(true);
            assertEquals("user-42", mockedExchange.getRequest().getHeaders().getFirst("X-User-Id"));
            assertEquals("USER", mockedExchange.getRequest().getHeaders().getFirst("X-User-Role"));
            return Mono.empty();
        };

        filter.apply(new JwtAuthenticationFilter.Config()).filter(exchange, chain).block();

        assertTrue(chainInvoked.get());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    void missingAuthorizationHeader_returns401_andStopsChain() {
        JwtAuthenticationFilter filter = newFilter();

        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/analyze").body("{}");
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        filter.apply(new JwtAuthenticationFilter.Config())
                .filter(exchange, m -> {
                    chainInvoked.set(true);
                    return Mono.empty();
                })
                .block();

        assertFalse(chainInvoked.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void invalidToken_returns401_andStopsChain() {
        JwtAuthenticationFilter filter = newFilter();
        String invalidToken = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyLTQyIn0.invalid";

        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/analyze")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + invalidToken)
                .body("{}");
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        filter.apply(new JwtAuthenticationFilter.Config())
                .filter(exchange, m -> {
                    chainInvoked.set(true);
                    return Mono.empty();
                })
                .block();

        assertFalse(chainInvoked.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void expiredToken_isRejected() {
        JwtUtil jwtUtil = jwtUtil();
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil, TestResponseWriter.create());
        String expiredToken = buildToken(jwtUtil, "expired-user", "alice", "USER",
                new Date(System.currentTimeMillis() - 60_000));

        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/analyze")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
                .body("{}");
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.apply(new JwtAuthenticationFilter.Config())
                .filter(exchange, m -> Mono.empty())
                .block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void tokenMissingRequiredClaims_isRejected() {
        JwtAuthenticationFilter filter = newFilter();
        // token without username/role claims must be rejected even though signature is valid
        String token = jwtUtil().generateToken("user-42");

        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/analyze")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body("{}");
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        filter.apply(new JwtAuthenticationFilter.Config())
                .filter(exchange, m -> {
                    chainInvoked.set(true);
                    return Mono.empty();
                })
                .block();

        assertFalse(chainInvoked.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void authWhitelistedPath_passesWithoutToken() {
        JwtAuthenticationFilter filter = newFilter();

        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/auth/login").body("{}");
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        filter.apply(new JwtAuthenticationFilter.Config())
                .filter(exchange, m -> {
                    chainInvoked.set(true);
                    return Mono.empty();
                })
                .block();

        assertTrue(chainInvoked.get());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    void malformedBearerHeader_returns401() {
        JwtAuthenticationFilter filter = newFilter();

        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/analyze")
                .header(HttpHeaders.AUTHORIZATION, "Bearerout xyz") // "Bearer " formatı bozuk
                .body("{}");
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        filter.apply(new JwtAuthenticationFilter.Config())
                .filter(exchange, m -> {
                    chainInvoked.set(true);
                    return Mono.empty();
                })
                .block();

        assertFalse(chainInvoked.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void clientSuppliedTrustedHeaders_cannotSpoofJwtClaims() {
        JwtUtil jwtUtil = jwtUtil();
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtUtil, TestResponseWriter.create());
        String token = buildToken(jwtUtil, "user-42", "alice", "USER",
                new Date(System.currentTimeMillis() + 60_000));

        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/analyze")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("X-User-Id", "attacker-id")
                .header("X-User-Role", "ADMIN")
                .body("{\"sourceCode\":\"test\"}");
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        GatewayFilterChain chain = mockedExchange -> {
            chainInvoked.set(true);
            assertEquals(List.of("user-42"), mockedExchange.getRequest().getHeaders().get("X-User-Id"));
            assertEquals(List.of("USER"), mockedExchange.getRequest().getHeaders().get("X-User-Role"));
            return Mono.empty();
        };

        filter.apply(new JwtAuthenticationFilter.Config()).filter(exchange, chain).block();

        assertTrue(chainInvoked.get());
    }

    private static String buildToken(JwtUtil jwtUtil, String userId, String username, String role, Date expiration) {
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET_B64));
        Claims claims = Jwts.claims().setSubject(userId);
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("role", role);
        claims.put("roles", List.of(role));

                return Jwts.builder()
                .setClaims(claims)
                .setIssuer("codementor")
                .setAudience("api-gateway")
                .setIssuedAt(new Date())
                .setExpiration(expiration)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
}