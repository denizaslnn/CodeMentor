package com.codementor.apigateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class JwtAuthenticationFilter extends AbstractGatewayFilterFactory<JwtAuthenticationFilter.Config> {

    public JwtAuthenticationFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {

            // 1. Header'dan Authorization değerini doğrudan çekiyoruz
            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            // 2. Başlık hiç yoksa (null) veya "Bearer " ile başlamıyorsa HTTP 401 dön
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            // 3. Mimarideki "Doğrulandı" akışı: İstek geçerli, sonraki adıma geç
            return chain.filter(exchange);
        };
    }
    public static class Config {
        // İleride bu filtreye özel ayarlar eklemek istersek burayı kullanacağız
    }
}