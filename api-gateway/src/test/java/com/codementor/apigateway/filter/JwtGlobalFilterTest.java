package com.codementor.apigateway.filter;

import com.codementor.apigateway.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class JwtGlobalFilterTest {

    private static final String SECRET_B64 = Base64.getEncoder().encodeToString(new byte[32]);
    private static final long VALIDITY_MS = 900_000L;

    private JwtUtil jwtUtil() {
        JwtUtil jwtUtil = new JwtUtil(SECRET_B64, VALIDITY_MS);
        jwtUtil.init();
        return jwtUtil;
    }

    @Test
    void validToken_withBody_addsUserHeadersAndContinuesChain() {
        JwtGlobalFilter filter = new JwtGlobalFilter(jwtUtil(), TestResponseWriter.create());
        String token = jwtUtil().generateToken("user-42", "alice", "USER");

        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/analyze")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body("{\"sourceCode\":\"test\"}");
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        GatewayFilterChain chain = mockedExchange -> {
            chainInvoked.set(true);
            assertEquals("user-42", mockedExchange.getRequest().getHeaders().getFirst("X-User-Id"));
            assertEquals("USER", mockedExchange.getRequest().getHeaders().getFirst("X-User-Role"));
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertTrue(chainInvoked.get());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    void missingAuthorizationHeader_returns401() {
        JwtGlobalFilter filter = new JwtGlobalFilter(jwtUtil(), TestResponseWriter.create());

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/status/abc").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        filter.filter(exchange, m -> {
            chainInvoked.set(true);
            return Mono.empty();
        }).block();

        assertFalse(chainInvoked.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void expiredToken_returns401() {
        JwtGlobalFilter filter = new JwtGlobalFilter(jwtUtil(), TestResponseWriter.create());
        String expiredToken = jwtUtil().generateToken("u-1", "alice", "USER", -60_000L);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/status/abc")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, m -> Mono.empty()).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void authPath_isWhitelisted() {
        JwtGlobalFilter filter = new JwtGlobalFilter(jwtUtil(), TestResponseWriter.create());

        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/auth/refresh").build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        filter.filter(exchange, m -> {
            chainInvoked.set(true);
            return Mono.empty();
        }).block();

        assertTrue(chainInvoked.get());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    void clientSuppliedTrustedHeaders_areOverwrittenWithJwtClaims() {
        JwtGlobalFilter filter = new JwtGlobalFilter(jwtUtil(), TestResponseWriter.create());
        String token = jwtUtil().generateToken("user-42", "alice", "USER");

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

        filter.filter(exchange, chain).block();

        assertTrue(chainInvoked.get());
    }
}