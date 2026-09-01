package com.codementor.aiservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * OpenAI uyumlu analiz endpoint'inin ayarlari.
 * <p>
 * Ayni istemci mock-vllm'e, kendi vLLM sunucumuza veya api.openai.com'a
 * baglanabilir; fark yalnizca bu degerlerdedir.
 * <p>
 * {@code apiKey} yalnizca environment/.env uzerinden gelir ve HICBIR log satirina
 * yazilmaz. Bu yuzden record'un {@code toString()}'i override edilmistir.
 */
@ConfigurationProperties(prefix = "ai.openai")
public record OpenAiProperties(
        String baseUrl,
        String model,
        String apiKey,
        Duration connectTimeout,
        Duration readTimeout) {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(60);

    public OpenAiProperties {
        if (connectTimeout == null) {
            connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        }
        if (readTimeout == null) {
            readTimeout = DEFAULT_READ_TIMEOUT;
        }
    }

    /** Bos/whitespace api-key "key yok" demektir: Authorization header'i gonderilmez. */
    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String toString() {
        return "OpenAiProperties[baseUrl=" + baseUrl
                + ", model=" + model
                + ", apiKey=" + (hasApiKey() ? "***" : "(yok)")
                + ", connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout + "]";
    }
}
