package com.codementor.aiservice.service;

import com.codementor.aiservice.config.OpenAiProperties;
import com.codementor.aiservice.dto.openai.ChatCompletionRequest;
import com.codementor.aiservice.dto.openai.ChatCompletionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * OpenAI uyumlu bir chat-completions endpoint'i uzerinden kod analizi yapar.
 * <p>
 * Ayni sinif mock-vllm'e, kendi vLLM sunucumuza ve api.openai.com'a hizmet eder;
 * hepsi ayni semayi konusur, fark {@link OpenAiProperties} degerlerindedir.
 */
@Slf4j
public class OpenAiCompatibleCodeAnalysisEngine implements CodeAnalysisEngine {

    private static final String SYSTEM_PROMPT =
            "Sen bir kod inceleme asistanisin. Verilen kaynak kodu, kullanicinin istegi "
                    + "dogrultusunda incele ve bulgularini kisa, maddeli bir sekilde Turkce anlat.";

    private final RestClient restClient;
    private final OpenAiProperties properties;

    public OpenAiCompatibleCodeAnalysisEngine(RestClient restClient, OpenAiProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public String analyze(String sourceCode, String prompt) {
        ChatCompletionRequest request = new ChatCompletionRequest(
                properties.model(),
                List.of(
                        ChatCompletionRequest.Message.system(SYSTEM_PROMPT),
                        ChatCompletionRequest.Message.user(buildUserMessage(sourceCode, prompt))));

        ChatCompletionResponse response;
        try {
            response = restClient.post()
                    .uri("/v1/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(this::applyAuthorization)
                    .body(request)
                    .retrieve()
                    .body(ChatCompletionResponse.class);
        } catch (RestClientException e) {
            // API key'i sizdirmamak icin yalnizca model adi ve hata tipi loglanir.
            throw new AnalysisEngineException(
                    "Analiz endpoint'i cagrilamadi. model=" + properties.model(), e);
        }

        String content = response == null ? null : response.firstContent();
        if (content == null || content.isBlank()) {
            throw new AnalysisEngineException(
                    "Analiz endpoint'i bos cevap dondu. model=" + properties.model());
        }
        log.info("Analiz tamamlandi. model={}, cevapUzunlugu={}", properties.model(), content.length());
        return content;
    }

    private void applyAuthorization(HttpHeaders headers) {
        if (properties.hasApiKey()) {
            headers.setBearerAuth(properties.apiKey());
        }
    }

    private String buildUserMessage(String sourceCode, String prompt) {
        String effectivePrompt = (prompt == null || prompt.isBlank())
                ? "Bu kodu genel olarak incele."
                : prompt;
        return effectivePrompt + "\n\n--- KAYNAK KOD ---\n" + (sourceCode == null ? "" : sourceCode);
    }
}
