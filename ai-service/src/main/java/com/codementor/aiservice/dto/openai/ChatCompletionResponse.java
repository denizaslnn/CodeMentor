package com.codementor.aiservice.dto.openai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * OpenAI / vLLM chat-completions cevabi. Yalnizca ihtiyac duyulan alanlar
 * modellenmistir; {@code usage}, {@code finish_reason} gibi alanlar ve
 * saglayiciya ozel ekstra alanlar bilincli olarak yok sayilir.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatCompletionResponse(String id, String model, List<Choice> choices) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(int index, Message message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(String role, String content) {
    }

    /** Cevaptaki ilk asistan mesajinin icerigi; yoksa {@code null}. */
    public String firstContent() {
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        Choice first = choices.get(0);
        return first == null || first.message() == null ? null : first.message().content();
    }
}
