package com.codementor.apigateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Prompt/AI (kod analiz) endpointine rate limit uygular.
 * <p>
 * Sadece {@code POST /api/v1/analyze} istekleri, Spring Cloud Gateway'in
 * {@link RedisRateLimiter}'ı ile Redis üzerinde token-bucket olarak sayılır.
 * Limit aşıldığında istek downstream'e GÖNDERİLMEZ ve anlaşılır HTTP 429 JSON
 * gövdesi döner. Diğer endpoint'ler (/api/v1/test, /api/v1/status/{id} vb.)
 * bu filtre tarafından sayılmaz ve doğrudan devam eder.
 * <p>
 * Bu dosya, eski hand-rolled sayma mantığının (3 istek/5sn + 60sn blok)
 * yerine, stabil token-bucket semantiği sağlayan RedisRateLimiter sınıfını
 * doğrudan kullanacak şekilde yeniden düzenlenmiştir.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestRateLimitingFilter implements GlobalFilter {

    private static final String RATE_LIMIT_ROUTE_ID = "code-service-analyze";
    private static final String ANALYZE_PATH = "/api/v1/analyze";

    private final RedisRateLimiter redisRateLimiter;
    private final KeyResolver keyResolver;

    public RequestRateLimitingFilter(RedisRateLimiter redisRateLimiter, KeyResolver keyResolver) {
        this.redisRateLimiter = redisRateLimiter;
        this.keyResolver = keyResolver;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Rate limit yalnızca prompt/AI endpoint'ine uygulanır
        if (!isAnalyzeRequest(exchange)) {
            return chain.filter(exchange);
        }

        return keyResolver.resolve(exchange)
                .flatMap(key -> redisRateLimiter.isAllowed(RATE_LIMIT_ROUTE_ID, key))
                .flatMap(response -> response.isAllowed()
                        ? chain.filter(exchange)  // limit içinde → downstream'e devam
                        : reject(exchange));       // limit aşıldı → 429, downstream'e gitmez
    }

    private boolean isAnalyzeRequest(ServerWebExchange exchange) {
        return HttpMethod.POST.equals(exchange.getRequest().getMethod())
                && ANALYZE_PATH.equals(exchange.getRequest().getPath().value());
    }

    private Mono<Void> reject(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String json = """
                {"timestamp":"%s","status":429,"error":"Too Many Requests",\
                "message":"Limit aşıldı: çok fazla istek gönderdiniz. Lütfen kısa bir süre bekleyip tekrar deneyin.",\
                "path":"%s"}
                """.formatted(Instant.now(), ANALYZE_PATH);

        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse()
                .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
    }
}
