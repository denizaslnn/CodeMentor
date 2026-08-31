package com.codementor.aiservice.service;

import org.springframework.stereotype.Component;

/**
 * MOCK analiz motoru (vLLM entegrasyonuna kadar kalıcı).
 * Davranış bilinçli olarak korunmuştur: ~3 saniye simülasyon süresi +
 * sabit başarı mesajı. vLLM aşamasında bu sınıf yerine
 * {@code VllmCodeAnalysisEngine} devreye alınacaktır.
 */
@Component
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