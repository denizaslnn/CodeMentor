package com.codementor.aiservice.dto.openai;

import java.util.List;

/**
 * OpenAI / vLLM {@code POST /v1/chat/completions} istek govdesi (kullandigimiz alt kume).
 */
public record ChatCompletionRequest(String model, List<Message> messages) {

    public record Message(String role, String content) {

        public static Message system(String content) {
            return new Message("system", content);
        }

        public static Message user(String content) {
            return new Message("user", content);
        }
    }
}
