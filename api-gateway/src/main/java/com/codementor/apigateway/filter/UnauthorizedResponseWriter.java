package com.codementor.apigateway.filter;

import com.codementor.apigateway.exception.ErrorResponse;
import com.codementor.apigateway.security.SecurityHeaders;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Writes a standardized {@code 401 Unauthorized} JSON body.
 * <p>
 * Security headers are applied here so authentication-failure responses (which
 * short-circuit the filter chain) still carry the gateway's hardening headers.
 */
@Component
public class UnauthorizedResponseWriter {

    public Mono<Void> write(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        SecurityHeaders.apply(exchange.getResponse().getHeaders());

        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                message,
                exchange.getRequest().getPath().value()
        );

        String body = "{\"success\":false,"
                + "\"message\":\"" + escapeJson(message) + "\","
                + "\"errorCode\":\"UNAUTHORIZED\","
                + "\"httpStatusCode\":" + HttpStatus.UNAUTHORIZED.value() + "}";

        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
