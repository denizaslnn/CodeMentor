package com.codementor.codeservice.publisher;

import com.codementor.codeservice.dto.CodeTaskMessage;
import com.codementor.codeservice.event.TaskCreatedEvent;
import com.codementor.codeservice.service.RedisStatusService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * RabbitMQ task publisher. {@link TaskCreatedEvent} transaction commit
 * edildikten sonra (AFTER_COMMIT) tetiklenir; böylece DB kaydı kalıcı
 * olmadan mesaj yayınlanmaz.
 * <p>
 * Redis status yazımı {@link RedisStatusService} üzerinden (TTL'li) yapılır;
 * source of truth PostgreSQL'dir.
 */
@Component
@Slf4j
public class RabbitMqTaskPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final RedisStatusService redisStatusService;
    private final String exchange;
    private final String routingKey;

    public RabbitMqTaskPublisher(RabbitTemplate rabbitTemplate,
                                 RedisStatusService redisStatusService,
                                 @Value("${rabbitmq.exchange:code-exchange}") String exchange,
                                 @Value("${rabbitmq.routingkey:code-routing-key}") String routingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.redisStatusService = redisStatusService;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskCreated(TaskCreatedEvent event) {
        String taskId = event.getTaskId();

        // Redis: hızlı status/read-model. TTL'li; kaybolursa PostgreSQL fallback devrede.
        redisStatusService.saveTaskStatus(taskId, "PENDING");

        CodeTaskMessage message = new CodeTaskMessage(taskId, event.getSourceCode(), event.getPrompt());
        log.info("RabbitMQ task yayınlanıyor. taskId={}, exchange={}, routingKey={}, status=PENDING",
                taskId, exchange, routingKey);
        rabbitTemplate.convertAndSend(exchange, routingKey, message);
    }
}
