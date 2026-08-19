package com.codementor.apigateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Configuration
public class RateLimiterConfig {

    /**
     * Kullanıcı/IP bazlı rate-limit anahtarı:
     * <ul>
     *   <li>JWT varsa "sub" (kullanıcı ID) → {@code user:<id>}</li>
     *   <li>JWT yoksa / sub okunamadıysa → {@code ip:<address>}</li>
     *   <li>son çare → {@code unknown}</li>
     * </ul>
     */
    @Bean
    public KeyResolver userAwareKeyResolver() {
        return exchange -> Mono.just(resolveKey(exchange));
    }

    /**
     * Spring Cloud Gateway'in Redis token-bucket'ı: saniyede 2 token dolar,
     * kova kapasitesi 5 (burst), istek başına 1 token harcanır.
     * ReactiveStringRedisTemplate, mevcut spring.data.redis (localhost:6379)
     * yapılandırmasından otomatik gelir — Docker Redis ile uyumlu.
     */
    @Bean
    public RedisRateLimiter redisRateLimiter(ReactiveStringRedisTemplate redisTemplate) {
        return new RedisRateLimiter(2, 5, 1); // replenishRate=2, burstCapacity=5, requestedTokens=1
    }

    private String resolveKey(ServerWebExchange exchange) {
        String auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth != null && auth.startsWith("Bearer ")) {
            String userId = extractSubject(auth.substring(7));
            if (userId != null && !userId.isBlank()) {
                return "user:" + userId;
            }
        }

        if (exchange.getRequest().getRemoteAddress() != null
                && exchange.getRequest().getRemoteAddress().getAddress() != null) {
            return "ip:" + exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }

        return "unknown";
    }

    /**
     * JWT payload'unu (base64url) decode edip {@code sub} claim'ini döndürür.
     * Malformed JWT ya da "sub" claim'i yoksa {@code null} döner — bu durumda
     * çağıran taraf IP anahtarına düşer. İmza doğrulaması yapılmaz; bu iş
     * JwtAuthenticationFilter'ın sorumluluğundadır.
     * <p>
     * NOT: api-gateway pom'unda jackson-databind compile scope'ta olmadığı
     * için (bağımlılık eklememek adına) JSON alanı basit bir string okuyucu
     * ile çözülür. Standart JWT payload'ları ({@code {"sub":"<id>",...}}) için
     * yeterlidir; herhangi bir ayrıştırma hatasında {@code null} dönülür.
     */
    static String extractSubject(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            return readStringField(payload, "sub");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * {@code {"field":"value", ...}} biçimindeki JSON'dan bir string alanı okur.
     * Alan yoksa veya değer string değilse {@code null} döner.
     */
    private static String readStringField(String json, String field) {
        String key = "\"" + field + "\"";
        int keyIndex = json.indexOf(key);
        if (keyIndex < 0) {
            return null;
        }
        int colonIndex = json.indexOf(':', keyIndex + key.length());
        if (colonIndex < 0) {
            return null;
        }
        int valueStart = json.indexOf('"', colonIndex + 1);
        if (valueStart < 0) {
            return null;
        }
        int valueEnd = json.indexOf('"', valueStart + 1);
        if (valueEnd < 0) {
            return null;
        }
        String value = json.substring(valueStart + 1, valueEnd);
        // JWT payload'lari base64url ile tasindigi icin ham tirsak (") nadirdir;
        // olursa " gibi escape'leri cozmek yeterlidir.
        return value.replace("\\\"", "\"");
    }
}
