package com.codementor.aiservice.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * MOCK analiz motoru: HTTP cagrisi yapmaz, sabit metin doner.
 * <p>
 * {@code ai.engine} verilmezse veya {@code mock} ise devrededir
 * ({@code matchIfMissing = true}), yani mevcut davranis korunur.
 * {@code ai.engine=openai} verildiginde yerini
 * {@link OpenAiCompatibleCodeAnalysisEngine} alir.
 */
@Component
@ConditionalOnProperty(name = "ai.engine", havingValue = "mock", matchIfMissing = true)
public class MockCodeAnalysisEngine implements CodeAnalysisEngine {

    @Override
    public String analyze(String sourceCode, String prompt) {
        try {
            Thread.sleep(3000); // AI analiz süresini simüle ediyoruz
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return "Kod analizi tamamlandı. Prompt: \"" + prompt + "\" doğrultusunda "
                + (sourceCode != null ? sourceCode.length() : 0)
                + " karakterlik kod incelendi. Herhangi bir kritik hata bulunamadı.";
    }
}