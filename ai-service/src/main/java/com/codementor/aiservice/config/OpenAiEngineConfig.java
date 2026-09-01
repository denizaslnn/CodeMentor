package com.codementor.aiservice.config;

import com.codementor.aiservice.service.CodeAnalysisEngine;
import com.codementor.aiservice.service.OpenAiCompatibleCodeAnalysisEngine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * {@code ai.engine=openai} verildiginde HTTP tabanli analiz motorunu devreye alir.
 * Property yoksa veya {@code mock} ise bu config hic yuklenmez ve in-process
 * {@code MockCodeAnalysisEngine} devrede kalir.
 */
@Configuration
@ConditionalOnProperty(name = "ai.engine", havingValue = "openai")
@EnableConfigurationProperties(OpenAiProperties.class)
public class OpenAiEngineConfig {

    /**
     * Analiz cagrilari icin ayri bir RestClient: timeout'lari uygulamanin geri
     * kalanindan bagimsiz olsun diye kendi request factory'si ile kurulur.
     * <p>
     * Bilincli olarak auto-configure edilmis {@code RestClient.Builder} bean'ine
     * bagimli DEGILIZ: ai-service'in classpath'inde (Spring Boot 4, yalnizca
     * webmvc starter'i) boyle bir bean yok ve olmasina da gerek yok -- baseUrl,
     * timeout ve header'lari zaten burada tam olarak belirliyoruz.
     */
    @Bean
    public RestClient openAiRestClient(OpenAiProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) properties.connectTimeout().toMillis());
        requestFactory.setReadTimeout((int) properties.readTimeout().toMillis());

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    public CodeAnalysisEngine openAiCodeAnalysisEngine(RestClient openAiRestClient,
                                                       OpenAiProperties properties) {
        return new OpenAiCompatibleCodeAnalysisEngine(openAiRestClient, properties);
    }
}
