package com.codementor.aiservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Consumer-owned RabbitMQ topology: durable task queue with a dead-letter
 * exchange (poison messages land in a DLQ instead of looping forever) + DLX/DLQ.
 * The exchange is declared here as well (idempotent); the producer
 * (code-service) declares the same exchange on the producer side.
 */
@Configuration
public class RabbitTopologyConfig {

    @Value("${rabbitmq.exchange}")
    private String exchangeName;

    @Value("${rabbitmq.routingkey}")
    private String routingKey;

    @Value("${rabbitmq.queue.name}")
    private String taskQueueName;

    @Value("${rabbitmq.dlx.name:code.analysis.dlx}")
    private String deadLetterExchangeName;

    @Value("${rabbitmq.dlq.name:code.analysis.dlq}")
    private String deadLetterQueueName;

    @Value("${rabbitmq.dlq.routingkey:code.analysis.dead}")
    private String deadLetterRoutingKey;

    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(exchangeName);
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(deadLetterExchangeName);
    }

    @Bean
    public Queue taskQueue() {
        return QueueBuilder.durable(taskQueueName)
                .withArgument("x-dead-letter-exchange", deadLetterExchangeName)
                .withArgument("x-dead-letter-routing-key", deadLetterRoutingKey)
                .build();
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(deadLetterQueueName).build();
    }

    @Bean
    public Binding taskBinding(Queue taskQueue, TopicExchange exchange) {
        return BindingBuilder.bind(taskQueue).to(exchange).with(routingKey);
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, TopicExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(deadLetterRoutingKey);
    }
}
