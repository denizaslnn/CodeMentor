package com.codementor.apigateway.filter;

import com.codementor.apigateway.security.AuthRateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * Per-IP brute-force protection for authentication endpoints
 * ({@code /api/v1/auth/**}).
 * <p>
 * Only acts on auth paths; all other requests pass through untouched. A client
 * exceeding {@code app.auth.ratelimit.max-attempts} within the configured window
 * receives {@code 429 Too Many Requests} with a {@code Retry-After} header. The
 * real client IP (TCP peer) is used as the key - not a spoofable
 * {@code X-Forwarded-For} - since the gateway is the public edge in this
 * deployment.
 */
@Component
@Slf4j
public class AuthRateLimitWebFilter implements GlobalFilter, Ordered {

    static final String AUTH_PREFIX = "/api/v1/auth/";

    private final AuthRateLimiter rateLimiter;
    private final UnauthorizedResponseWriter unauthorizedResponseWriter;

    public AuthRateLimitWebFilter(AuthRateLimiter rateLimiter, UnauthorizedResponseWriter unauthorizedResponseWriter) {
        this.rateLimiter = rateLimiter;
        this.unauthorizedResponseWriter = unauthorizedResponseWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith(AUTH_PREFIX)) {
            return chain.filter(exchange);
        }

        String clientId = resolveClientIp(exchange);
        return rateLimiter.tryAcquire(clientId)
                .flatMap(allowed -> {
                    if (allowed) {
                        return chain.filter(exchange);
                    }
                    log.warn("Auth brute-force limit exceeded. client-ip={}, path={}", clientId, path);
                    return tooManyRequests(exchange, rateLimiter.getWindowSeconds());
                });
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        if (remote != null && remote.getAddress() != null) {
            return remote.getAddress().getHostAddress();
        }
        return "unknown";
    }

    private Mono<Void> tooManyRequests(ServerWebExchange exchange, int retryAfterSeconds) {
        exchange.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        return unauthorizedResponseWriter
                .write(exchange, HttpStatus.TOO_MANY_REQUESTS,
                        "error.auth.too-many-requests", "TOO_MANY_REQUESTS")
                .doOnTerminate(() -> log.debug("Returned 429 for auth rate-limit."));
    }

    @Override
    public int getOrder() {
        // After JwtGlobalFilter (auth, HIGHEST) + SecurityHeadersWebFilter (HIGHEST+1).
        return Ordered.HIGHEST_PRECEDENCE + 2;
    }
}
