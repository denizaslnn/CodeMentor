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

    private static final String AUTH_WHITELIST_PREFIX = "/api/v1/auth";
    private static final String X_USER_ID_HEADER = "X-User-Id";
    private static final String X_USER_ROLE_HEADER = "X-User-Role";

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
            // Authentication endpoints manage their own credentials -> no access token required.
            // Even here, client-supplied trusted headers are stripped.
            if (exchange.getRequest().getPath().value().startsWith(AUTH_WHITELIST_PREFIX)) {
                ServerWebExchange stripped = exchange.mutate()
                        .request(r -> r.headers(headers -> {
                            headers.remove(X_USER_ID_HEADER);
                            headers.remove(X_USER_ROLE_HEADER);
                        }))
                        .build();
                return chain.filter(stripped);
            }

            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("Authorization header missing or invalid format");
                return unauthorizedResponseWriter.write(exchange, "Authorization header missing or invalid format.");
            }

            String token = authHeader.substring(7);
            try {
                Jws<Claims> jws = jwtUtil.validateAndParse(token);

                if (!jwtUtil.hasRequiredClaims(jws)) {
                    log.warn("JWT is missing required claims");
                    return unauthorizedResponseWriter.write(exchange, "JWT token is invalid or expired.");
                }

                // Prefer explicit userId claim, fallback to subject
                String userIdClaim = jwtUtil.getClaimAsString(jws, "userId");
                String finalUserId = (userIdClaim != null) ? userIdClaim : jwtUtil.getSubject(jws);
                String role = jwtUtil.getRole(jws);

                // Add X-User-Id / X-User-Role headers for downstream services.
                // Client-supplied values are removed first so they can never be spoofed.
                ServerWebExchange mutated = exchange.mutate()
                        .request(r -> {
                            r.headers(headers -> {
                                headers.remove(X_USER_ID_HEADER);
                                headers.remove(X_USER_ROLE_HEADER);
                            });
                            r.header(X_USER_ID_HEADER, finalUserId != null ? finalUserId : "");
                            r.header(X_USER_ROLE_HEADER, role != null ? role : "");
                        })
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