package com.codementor.apigateway.filter;

import com.codementor.apigateway.security.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import reactor.core.publisher.Flux;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class JwtGlobalFilter implements GlobalFilter, Ordered {

    private final JwtUtil jwtUtil;

    public JwtGlobalFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        // Apply only to API routes
        if (!path.startsWith("/api/v1")) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or invalid Authorization header for {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);
        try {
            Jws<Claims> jws = jwtUtil.validateAndParse(token);

            // Extract user id (subject or userId claim)
            String userId = jwtUtil.getClaimAsString(jws, "userId");
            if (userId == null) {
                userId = jwtUtil.getSubject(jws);
            }

            ServerHttpRequest request = exchange.getRequest();
            long contentLength = request.getHeaders().getContentLength();
            // make final copies for lambda capture
            final ServerWebExchange finalExchange = exchange;
            final String finalUserId = userId;
            final ServerHttpRequest finalRequest = request;

            // If there is no body, just add header and continue.
            if (contentLength == 0) {
                ServerHttpRequest mutated = request.mutate().header("X-User-Id", userId != null ? userId : "").build();
                return chain.filter(exchange.mutate().request(mutated).build());
            }

            // Preserve body: read it into memory, then recreate a request decorator that provides the cached body.
            return DataBufferUtils.join(request.getBody())
                    .defaultIfEmpty(new DefaultDataBufferFactory().wrap(new byte[0]))
                    .flatMap(dataBuffer -> {
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        DataBufferUtils.release(dataBuffer);

                        ServerHttpRequestDecorator decorated = new ServerHttpRequestDecorator(request) {
                            @Override
                            public Flux<DataBuffer> getBody() {
                                DataBufferFactoryWrapper factory = new DataBufferFactoryWrapper(finalExchange);
                                DataBuffer buf = factory.wrap(bytes);
                                return Flux.just(buf);
                            }
                        };

                        ServerHttpRequest mutated = decorated.mutate()
                                                                .header("X-User-Id", finalUserId != null ? finalUserId : "")
                                .build();

                                                        return chain.filter(finalExchange.mutate().request(mutated).build());
                    });
        } catch (JwtException ex) {
            log.error("JWT validation failed: {}", ex.getMessage());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    @Override
    public int getOrder() {
        // High precedence so auth is checked early
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
