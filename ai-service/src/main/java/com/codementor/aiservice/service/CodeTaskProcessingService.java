package com.codementor.aiservice.service;

import com.codementor.aiservice.dto.CodeTaskMessage;
import com.codementor.aiservice.entity.AnalysisRequest;
import com.codementor.aiservice.repository.AnalysisRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Idempotent code-task processor.
 * <p>
 * Designed for at-least-once RabbitMQ delivery:
 * <ul>
 *   <li>Duplicate deliveries of a COMPLETED / FAILED task are no-ops.</li>
 *   <li>A task stuck PROCESSING after a crash is safely reprocessed.</li>
 *   <li>Analysis errors are persisted as FAILED and NOT re-queued, so a failing
 *       task cannot poison the queue (no infinite retry loop).</li>
 * </ul>
 * The durable source of truth is PostgreSQL; Redis is only a TTL'd read model.
 */
@Service
@Slf4j
public class CodeTaskProcessingService {

    private final AnalysisRepository analysisRepository;
    private final RedisStatusService redisStatusService;
    private final CodeAnalysisEngine analysisEngine;

    public CodeTaskProcessingService(AnalysisRepository analysisRepository,
                                     RedisStatusService redisStatusService,
                                     CodeAnalysisEngine analysisEngine) {
        this.analysisRepository = analysisRepository;
        this.redisStatusService = redisStatusService;
        this.analysisEngine = analysisEngine;
    }

    public void process(CodeTaskMessage message) {
        String taskId = message.getTaskId();
        if (taskId == null || taskId.isBlank()) {
            log.warn("Boş taskId'li mesaj yok sayıldı.");
            return;
        }

        Optional<AnalysisRequest> existing = analysisRepository.findById(taskId);
        if (existing.isEmpty()) {
            log.warn("Task veritabanında bulunamadı; yok sayıldı. taskId={}", taskId);
            return;
        }

        AnalysisRequest request = existing.get();
        String status = request.getStatus();
        if ("COMPLETED".equals(status)) {
            log.info("Task zaten COMPLETED; idempotency atlama. taskId={}", taskId);
            return;
        }
        if ("FAILED".equals(status)) {
            log.warn("Task daha önce FAILED; yok sayıldı. taskId={}", taskId);
            return;
        }

        long start = System.nanoTime();
        log.info("status={} -> PROCESSING. taskId={}", status, taskId);
        redisStatusService.updateStatus(taskId, "PROCESSING");
        request.setStatus("PROCESSING");
        analysisRepository.save(request);

        try {
            String result = analysisEngine.analyze(message.getSourceCode(), message.getPrompt());
            redisStatusService.updateCompleted(taskId, result);
            request.setStatus("COMPLETED");
            request.setAiResponse(result);
            analysisRepository.save(request);
            long ms = (System.nanoTime() - start) / 1_000_000;
            log.info("status=PROCESSING -> COMPLETED. taskId={}, durationMs={}", taskId, ms);
        } catch (Exception e) {
            long ms = (System.nanoTime() - start) / 1_000_000;
            log.error("Analiz başarısız; FAILED olarak kaydedildi. taskId={}, durationMs={}, error={}",
                    taskId, ms, e.getMessage(), e);
            redisStatusService.updateStatus(taskId, "FAILED");
            request.setStatus("FAILED");
            analysisRepository.save(request);
            // Swallow: the container ACKs the message so it is not re-queued,
            // preventing a poison-task retry loop.
        }
    }
}
