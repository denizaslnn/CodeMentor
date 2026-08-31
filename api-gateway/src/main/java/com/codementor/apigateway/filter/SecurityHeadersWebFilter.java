package com.codementor.apigateway.filter;

import com.codementor.apigateway.security.SecurityHeaders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Adds security headers to the response for all non-error (2xx) traffic.
 * <p>
 * Runs just after {@link com.codementor.apigateway.filter.JwtGlobalFilter}
 * ({@link Ordered#HIGHEST_PRECEDENCE}) so that for valid/whitelisted requests
 * the headers are present before the body is written. 401 responses produced by
 * {@link UnauthorizedResponseWriter} apply the same header set themselves,
 * since those paths short-circuit before this filter executes.
 */
@Component
@Slf4j
public class SecurityHeadersWebFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange,
                             org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (SecurityHeaders.isDocsPath(path)) {
            // Swagger UI cannot render under the strict API CSP.
            SecurityHeaders.applyForDocs(exchange.getResponse().getHeaders());
        } else {
            SecurityHeaders.apply(exchange.getResponse().getHeaders());
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        // Immediately after JwtGlobalFilter (HIGHEST_PRECEDENCE) on the request side.
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
