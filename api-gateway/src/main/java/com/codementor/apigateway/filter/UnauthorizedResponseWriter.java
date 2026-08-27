package com.codementor.apigateway.filter;

import com.codementor.apigateway.exception.ErrorResponse;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class UnauthorizedResponseWriter {

    public Mono<Void> write(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                message,
                exchange.getRequest().getPath().value()
        );

        String body = "{\"timestamp\":\"" + errorResponse.timestamp().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "\","
                + "\"status\":" + errorResponse.status() + ","
                + "\"error\":\"" + escapeJson(errorResponse.error()) + "\","
                + "\"message\":\"" + escapeJson(errorResponse.message()) + "\","
                + "\"path\":\"" + escapeJson(errorResponse.path()) + "\"}";

        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
