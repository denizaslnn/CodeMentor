package com.codementor.aiservice.consumer;

import com.codementor.aiservice.dto.CodeTaskMessage;
import com.codementor.aiservice.entity.AnalysisRequest;
import com.codementor.aiservice.repository.AnalysisRepository;
import com.codementor.aiservice.service.AiAnalysisService;
import com.codementor.aiservice.service.RedisStatusService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Slf4j
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
        log.info("Kuyruktan mesaj alındı. taskId={}", taskId);

        try {
            redisStatusService.updateStatus(taskId, "PROCESSING");
            updateDbStatus(taskId, "PROCESSING", null);

            String result = aiAnalysisService.analyze(message.getCode(), message.getLanguage());

            redisStatusService.updateCompleted(taskId, result);
            updateDbStatus(taskId, "COMPLETED", result);

            log.info("Analiz tamamlandı. taskId={}", taskId);

        } catch (Exception e) {
            log.error("Analiz sırasında hata oluştu. taskId={}, error={}", taskId, e.getMessage(), e);
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
            log.warn("Task veritabanında bulunamadı. taskId={}", taskId);
        }
    }
}