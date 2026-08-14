package config;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;
@Configuration
public class RateLimiterConfig {



        // MİMARİ ADIMI: "Redis Rate Limiter Kontrolü"
        // BURADA NE YAPIYORUZ?: Sisteme saniyede kaç istek gelebileceğinin kurallarını belirliyoruz.
        @Bean
        public RedisRateLimiter redisRateLimiter() {
            // replenishRate (2): Bir kullanıcının saniyede yapabileceği standart istek sayısı (Örn: Saniyede 2 istek)
            // burstCapacity (5): Anlık yoğunlukta (sistem müsaitse) kullanıcının çıkabileceği maksimum istek sayısı
            // Eğer kullanıcı bu sayıları aşarsa, Gateway otomatik olarak "HTTP 429 Too Many Requests" dönecektir.
            return new RedisRateLimiter(2, 5);
        }

        // MİMARİ ADIMI: Kullanıcıyı Ayırt Etme (Rate Limiter'ın alt yapısı)
        // BURADA NE YAPIYORUZ?: Sınırlandırmayı neye göre yapacağımızı belirliyoruz.
        // Tüm sisteme değil, "IP adresine göre" kişi bazlı sınırlandırma yapıyoruz.
        @Bean
        public KeyResolver userKeyResolver() {
            return exchange -> Mono.just(
                    // İsteği atan kişinin IP adresini al ve Redis'e bu IP üzerinden kota koy.
                    exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
            );
        }
    }


