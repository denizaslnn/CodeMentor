package com.codementor.aiservice.consumer;

import com.codementor.aiservice.dto.CodeTaskMessage;
import com.codementor.aiservice.service.CodeTaskProcessingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ consumer for code-analysis tasks.
 * <p>
 * Thin boundary around {@link RabbitListener}: all processing (idempotency,
 * status lifecycle, analysis, DB/Redis writes) lives in
 * {@link CodeTaskProcessingService}. Failures are persisted as FAILED inside
 * the processor and the message is ACK'd, so a failing task cannot poison the
 * queue (no requeue/retry loop).
 */
@Component
@Slf4j
public class CodeTaskConsumer {

    private final CodeTaskProcessingService processingService;

    public CodeTaskConsumer(CodeTaskProcessingService processingService) {
        this.processingService = processingService;
    }

    @RabbitListener(queues = "${rabbitmq.queue.name}")
    public void consumeMessage(CodeTaskMessage message) {
        if (message == null) {
            log.warn("Null mesaj alındı, yok sayıldı.");
            return;
        }
        log.info("Kuyruktan mesaj alındı. taskId={}", message.getTaskId());
        processingService.process(message);
    }
}
