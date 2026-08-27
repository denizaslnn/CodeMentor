package com.codementor.apigateway.filter;

import com.codementor.apigateway.security.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class JwtAuthenticationFilter extends AbstractGatewayFilterFactory<JwtAuthenticationFilter.Config> {

    private static final String WHITELISTED_PATH = "/api/v1/get-token";

    private final JwtUtil jwtUtil;
    private final UnauthorizedResponseWriter unauthorizedResponseWriter;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, UnauthorizedResponseWriter unauthorizedResponseWriter) {
        super(Config.class);
        this.jwtUtil = jwtUtil;
        this.unauthorizedResponseWriter = unauthorizedResponseWriter;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            // Whitelisted test endpoints skip JWT validation
            if (WHITELISTED_PATH.equals(exchange.getRequest().getPath().value())) {
                return chain.filter(exchange);
            }

            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("Authorization header missing or invalid format");
                return unauthorizedResponseWriter.write(exchange, "Authorization header missing or invalid format.");
            }

            String token = authHeader.substring(7);
            try {
                Jws<Claims> jws = jwtUtil.validateAndParse(token);

                // Prefer explicit userId claim, fallback to subject
                String userIdClaim = jwtUtil.getClaimAsString(jws, "userId");
                String finalUserId = (userIdClaim != null) ? userIdClaim : jwtUtil.getSubject(jws);

                // Add X-User-Id header for downstream services
                ServerWebExchange mutated = exchange.mutate()
                        .request(r -> r.header("X-User-Id", finalUserId != null ? finalUserId : ""))
                        .build();

                return chain.filter(mutated);

            } catch (JwtException ex) {
                log.error("JWT validation failed: {}", ex.getMessage());
                return unauthorizedResponseWriter.write(exchange, "JWT token is invalid or expired.");
            }
        };
    }

    public static class Config {
        // filter config placeholder
    }
}