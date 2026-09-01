package com.codementor.aiservice.service;

/**
 * Analiz motorunun (LLM cagrisi) basarisiz oldugunu bildirir.
 * <p>
 * {@code CodeTaskProcessingService} bunu yakalayip task'i FAILED olarak kaydeder;
 * mesaj yalnizca log/DB icindir, son kullaniciya i18n edilmis metin donmez.
 */
public class AnalysisEngineException extends RuntimeException {

    public AnalysisEngineException(String message) {
        super(message);
    }

    public AnalysisEngineException(String message, Throwable cause) {
        super(message, cause);
    }
}
