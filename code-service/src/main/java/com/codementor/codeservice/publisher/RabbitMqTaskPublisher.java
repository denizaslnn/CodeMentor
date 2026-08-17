package com.codementor.codeservice.publisher;

import com.codementor.codeservice.event.TaskCreatedEvent;
import com.codementor.codeservice.CodeTaskMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

@Component
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
            System.err.println("Redis set failed in TaskCreated listener: " + e.getMessage());
        }

        CodeTaskMessage msg = new CodeTaskMessage(taskId, event.getSourceCode(), event.getPrompt());
        System.out.println("[TransactionalEventListener] Publishing task to RabbitMQ: " + taskId);
        rabbitTemplate.convertAndSend(exchange, routingKey, msg);
    }
}
