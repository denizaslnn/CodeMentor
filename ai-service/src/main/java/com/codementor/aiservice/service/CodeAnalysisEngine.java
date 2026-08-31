package com.codementor.aiservice.service;

/**
 * Kod analiz motoru abstraction'ı.
 * <p>
 * vLLM entegrasyonu ileride ayrı bir aşamada yapılacaktır; o zaman tek
 * yapılması gereken, yeni bir implementation (ör.
 * {@code VllmCodeAnalysisEngine}) ekleyip Spring bean olarak register
 * etmektir. Mock davranış şu an {@link MockCodeAnalysisEngine} ile korunur.
 */
public interface CodeAnalysisEngine {

    /**
     * Verilen kaynak kodu ve promptu analiz eder, insan-okunur analiz
     * sonucu metnini döndürür.
     */
    String analyze(String sourceCode, String prompt);
}