package com.codementor.aiservice.consumer;

import com.codementor.aiservice.dto.CodeTaskMessage;
import com.codementor.aiservice.entity.AnalysisRequest;
import com.codementor.aiservice.repository.AnalysisRepository;
import com.codementor.aiservice.service.RedisStatusService;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CodeTaskConsumer {

    private final AnalysisRepository repository;
    private final RedisStatusService redisStatusService;

    public CodeTaskConsumer(AnalysisRepository repository, RedisStatusService redisStatusService) {
        this.repository = repository;
        this.redisStatusService = redisStatusService;
    }

    @RabbitListener(queues = "${rabbitmq.queue.name}")
        public void consumeMessage(CodeTaskMessage message) {
            String taskId = message.getTaskId();

            // Try to find the DB record with retries to handle slight commit delays
            final int maxAttempts = 6;
            final long waitMs = 250L;
            AnalysisRequest request = null;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                request = repository.findById(taskId).orElse(null);
                if (request != null) break;
                try {
                    Thread.sleep(waitMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            if (request == null) {
                System.err.println("Geçersiz mesaj, kuyruktan düşürülüyor: Task DB'de bulunamadı: " + taskId);
                throw new AmqpRejectAndDontRequeueException("Task DB'de bulunamadı: " + taskId);
            }

            try {
                redisStatusService.updateTaskStatus(taskId, "PROCESSING");

                String aiResult = executeAiAnalysis(request.getSourceCode(), request.getPrompt());

                redisStatusService.updateTaskStatus(taskId, "COMPLETED");

                // Update DB in a transactional helper to avoid long-lived consumer transaction
                updateDbResult(taskId, aiResult);

                System.out.println("✅ [ai-service] Analiz tamamlandı. Task ID: " + taskId);

            } catch (Exception e) {
                System.err.println("AI Analizi sırasında hata oluştu. TaskID: " + taskId + " | " + e.getMessage());
                redisStatusService.updateTaskStatus(taskId, "FAILED");
                updateDbStatus(taskId, "FAILED");
                throw new AmqpRejectAndDontRequeueException("Sistem hatası nedeniyle mesaj düşürülüyor.", e);
            }
        }

    private String executeAiAnalysis(String sourceCode, String prompt) {
        return "Kod analizi başarılı: Temiz görünüyor.";
    }

    @Transactional
    protected void updateDbResult(String taskId, String aiResult) {
        repository.findById(taskId).ifPresent(request -> {
            request.setStatus("COMPLETED");
            request.setAiResponse(aiResult);
            repository.save(request);
        });
    }

    private void updateDbStatus(String taskId, String status) {
        repository.findById(taskId).ifPresent(request -> {
            request.setStatus(status);
            repository.save(request);
        });
    }
}
