package com.codementor.codeservice.publisher;

import com.codementor.codeservice.CodeTaskMessage;
import com.codementor.codeservice.event.TaskCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

@Component
@Slf4j
public class RabbitMqTaskPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${rabbitmq.exchange:code-exchange}")
    private String exchange;

    @Value("${rabbitmq.routingkey:code-routing-key}")
    private String routingKey;

    public RabbitMqTaskPublisher(RabbitTemplate rabbitTemplate, RedisTemplate<String, String> redisTemplate) {
        this.rabbitTemplate = rabbitTemplate;
        this.redisTemplate = redisTemplate;
        // Ensure the RabbitTemplate uses Jackson JSON converter so payloads are sent as application/json
        @SuppressWarnings("deprecation")
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        this.rabbitTemplate.setMessageConverter(converter);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskCreated(TaskCreatedEvent event) {
        String taskId = event.getTaskId();
        try {
            redisTemplate.opsForValue().set("task:" + taskId, "PENDING");
        } catch (Exception e) {
            log.error("Redis başlangıç durumu kaydı başarısız. taskId={}, error={}", taskId, e.getMessage(), e);
        }

        CodeTaskMessage msg = new CodeTaskMessage(taskId, event.getSourceCode(), event.getPrompt());
        log.info("RabbitMQ task yayınlanıyor. taskId={}", taskId);
        rabbitTemplate.convertAndSend(exchange, routingKey, msg);
    }
}
