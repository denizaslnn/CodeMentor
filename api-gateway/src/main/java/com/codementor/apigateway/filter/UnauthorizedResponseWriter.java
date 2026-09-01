package com.codementor.apigateway.filter;

import com.codementor.apigateway.config.LocalizedMessageResolver;
import com.codementor.apigateway.dto.ApiResponse;
import com.codementor.apigateway.security.SecurityHeaders;
// Spring Boot 4 -> Jackson 3: databind paketi tools.jackson.databind, ve
// auto-configure edilen ObjectMapper bean'i de bu tiptedir.
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Writes standardized JSON error bodies for security failures in the gateway
 * filter chain (401/403/429 etc.).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UnauthorizedResponseWriter {

    private final ObjectMapper objectMapper;
    private final LocalizedMessageResolver localizedMessageResolver;

    public Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String messageKey, String errorCode, Object... args) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        SecurityHeaders.apply(exchange.getResponse().getHeaders());

        String localizedMessage = localizedMessageResolver.resolve(messageKey, exchange, args);
        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(false)
                .message(localizedMessage)
                .errorCode(errorCode)
                .httpStatusCode(status.value())
                .build();

        byte[] bodyBytes = serializeSafely(response, status.value(), errorCode);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bodyBytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private byte[] serializeSafely(ApiResponse<Void> response, int status, String errorCode) {
        try {
            return objectMapper.writeValueAsBytes(response);
        } catch (Exception ex) {
            log.error("Failed to serialize API error response. status={}, errorCode={}", status, errorCode, ex);
                        String fallback = "{\"success\":false,\"message\":\"An unexpected error occurred\","
                    + "\"errorCode\":\"INTERNAL_ERROR\",\"httpStatusCode\":500}";
            return fallback.getBytes(StandardCharsets.UTF_8);
        }
    }
}
