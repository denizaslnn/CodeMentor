package com.codementor.aiservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class RabbitTopologyConfig {

    @Value("${rabbitmq.queue.name}")
    private String taskQueueName;

    @Value("${rabbitmq.exchange}")
    private String exchangeName;

    @Value("${rabbitmq.routingkey}")
    private String routingKey;

    @Value("${rabbitmq.result.routingkey}")
    private String resultRoutingKey;

    @Value("${rabbitmq.result.queue}")
    private String resultQueueName;

    @Bean
    public Queue taskQueue() {
        return new Queue(taskQueueName, true);
    }

    @Bean
    public Queue resultQueue() {
        return new Queue(resultQueueName, true);
    }

    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(exchangeName);
    }

    @Bean
    public Binding taskBinding(Queue taskQueue, TopicExchange exchange) {
        return BindingBuilder.bind(taskQueue).to(exchange).with(routingKey);
    }

    @Bean
    public Binding resultBinding(Queue resultQueue, TopicExchange exchange) {
        return BindingBuilder.bind(resultQueue).to(exchange).with(resultRoutingKey);
    }
}
