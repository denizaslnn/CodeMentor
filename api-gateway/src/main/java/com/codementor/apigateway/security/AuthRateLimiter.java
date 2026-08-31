package com.codementor.apigateway.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Per-client fixed-window counter for brute-force protection on the
 * authentication endpoints ({@code /api/v1/auth/**}).
 * <p>
 * Uses Redis so the limit is shared across gateway instances. On Redis failure
 * the filter <b>fail-opens</b> (logs a warning and allows the request) - auth
 * availability is preferred over locking legitimate users out when the store
 * is down. A leaked access token is short-lived (15 min); a denied login during
 * a Redis outage would block all auth, so fail-open is the deliberate choice.
 */
@Component
@Slf4j
public class AuthRateLimiter {

    static final String KEY_PREFIX = "auth_rl:";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final int maxAttempts;
    private final int windowSeconds;
    private final Duration window;

    public AuthRateLimiter(ReactiveStringRedisTemplate redisTemplate,
                           @Value("${app.auth.ratelimit.max-attempts:10}") int maxAttempts,
                           @Value("${app.auth.ratelimit.window-seconds:60}") int windowSeconds) {
        this.redisTemplate = redisTemplate;
        this.maxAttempts = maxAttempts;
        this.windowSeconds = windowSeconds;
        this.window = Duration.ofSeconds(windowSeconds);
    }

    public int getWindowSeconds() {
        return windowSeconds;
    }

    /**
     * Atomically records one attempt for {@code clientId} within the fixed window.
     *
     * @return {@code true} if the request should be allowed, {@code false} if the
     *         per-client limit has been exceeded.
     */
    public Mono<Boolean> tryAcquire(String clientId) {
        if (clientId == null || clientId.isBlank() || maxAttempts <= 0) {
            return Mono.just(true);
        }
        String key = KEY_PREFIX + clientId;
        return redisTemplate.opsForValue().increment(key)
                .switchIfEmpty(Mono.just(0L))
                .flatMap(count -> count == 1L
                        ? redisTemplate.expire(key, window).thenReturn(count)
                        : Mono.just(count))
                .map(count -> count <= maxAttempts)
                .onErrorResume(e -> {
                    log.warn("Auth rate-limit store unavailable; allowing request (fail-open) for client={}", clientId, e);
                    return Mono.just(true);
                });
    }
}
