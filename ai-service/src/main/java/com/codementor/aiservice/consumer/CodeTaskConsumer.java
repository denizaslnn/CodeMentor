package com.codementor.aiservice.consumer;

import com.codementor.aiservice.dto.CodeTaskMessage;
import com.codementor.aiservice.entity.AnalysisRequest;
import com.codementor.aiservice.repository.AnalysisRepository;
import com.codementor.aiservice.service.AiAnalysisService;
import com.codementor.aiservice.service.RedisStatusService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class CodeTaskConsumer {

    private final RedisStatusService redisStatusService;
    private final AiAnalysisService aiAnalysisService;
    private final AnalysisRepository analysisRepository;

    public CodeTaskConsumer(RedisStatusService redisStatusService,
                            AiAnalysisService aiAnalysisService,
                            AnalysisRepository analysisRepository) {
        this.redisStatusService = redisStatusService;
        this.aiAnalysisService = aiAnalysisService;
        this.analysisRepository = analysisRepository;
    }

    // Adım 8: Consume Message from Queue
    @RabbitListener(queues = "${rabbitmq.queue.name}")
    public void consumeMessage(CodeTaskMessage message) {
        String taskId = message.getTaskId();
        System.out.println("📩 [ai-service] Kuyruktan mesaj alındı! Task ID: " + taskId);

        try {
            // Adım 9: Update Cache to PROCESSING
            redisStatusService.updateStatus(taskId, "PROCESSING");
            updateDbStatus(taskId, "PROCESSING", null);

            // Adım 10: Execute AI / LLM Code Analysis
            String result = aiAnalysisService.analyze(message.getCode(), message.getLanguage());

            // Adım 11: Update Cache to COMPLETED + Analiz Sonucu
            redisStatusService.updateCompleted(taskId, result);

            // Adım 12: Update DB (Status: COMPLETED, ai_response = Result)
            updateDbStatus(taskId, "COMPLETED", result);

            System.out.println("✅ [ai-service] Analiz tamamlandı. Task ID: " + taskId);

        } catch (Exception e) {
            System.err.println("❌ [ai-service] Analiz sırasında hata (taskId=" + taskId + "): " + e.getMessage());
            updateDbStatus(taskId, "FAILED", e.getMessage());
            redisStatusService.updateStatus(taskId, "FAILED");
        }
    }

    private void updateDbStatus(String taskId, String status, String aiResponse) {
        Optional<AnalysisRequest> optionalRequest = analysisRepository.findById(taskId);
        if (optionalRequest.isPresent()) {
            AnalysisRequest request = optionalRequest.get();
            request.setStatus(status);
            if (aiResponse != null) {
                request.setAiResponse(aiResponse);
            }
            analysisRepository.save(request);
        } else {
            System.out.println("⚠️ [ai-service] Task DB'de bulunamadı: " + taskId);
        }
    }
}