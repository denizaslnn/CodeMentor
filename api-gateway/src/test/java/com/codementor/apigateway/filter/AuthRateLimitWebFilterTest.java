package com.codementor.apigateway.filter;

import com.codementor.apigateway.security.AuthRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthRateLimitWebFilterTest {

    @Mock
    private AuthRateLimiter rateLimiter;
    @Mock
    private GatewayFilterChain chain;

    private AuthRateLimitWebFilter filter;

    @BeforeEach
    void setUp() {
        filter = new AuthRateLimitWebFilter(rateLimiter);
    }

    @Test
    void nonAuthPath_passesThroughWithoutRateLimiting() {
        when(chain.filter(any())).thenReturn(Mono.empty());

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/analyze").build());

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
        verifyNoInteractions(rateLimiter);
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    void authPath_allowed_passesToChain() {
        when(chain.filter(any())).thenReturn(Mono.empty());
                when(rateLimiter.tryAcquire(anyString())).thenReturn(Mono.just(true));


        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/auth/login").build());

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    void authPath_rateLimited_returns429AndRetryAfter() {
        when(rateLimiter.tryAcquire(anyString())).thenReturn(Mono.just(false));
        when(rateLimiter.getWindowSeconds()).thenReturn(60);

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/auth/login").build());

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
        assertEquals("60", exchange.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        verify(chain, never()).filter(any());
    }
}
