package com.codementor.aiservice.service;

import org.springframework.stereotype.Service;

@Service
public class AiAnalysisService {

    /**
     * vLLM entegrasyonu gelene kadar analiz sonucunu simüle eder.
     * İleride burada bir RestTemplate/WebClient ile vLLM endpoint'ine
     * istek atılacak.
     */
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