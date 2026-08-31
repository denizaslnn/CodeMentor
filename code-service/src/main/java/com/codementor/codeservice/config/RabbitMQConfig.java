package com.codementor.codeservice.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * code-service RabbitMQ yapılandırması.
 * <p>
 * code-service yalnızca PRODUCER'dır: exchange'i declare eder ve mesaj
 * yayınlar. Main queue (code.analysis.queue), DLX/DLQ ve binding'ler
 * CONSUMER tarafında (ai-service RabbitTopologyConfig) declare edilir —
 * consumer-owned queue prensibi.
 * <p>
 * JSON serialization (Jackson2JsonMessageConverter) kullanılır; Java
 * serialization header'larına bağımlılık yoktur.
 */
@Configuration
public class RabbitMQConfig {

    @Value("${rabbitmq.exchange}")
    private String exchangeName;

    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(exchangeName);
    }

    @Bean
    @SuppressWarnings("deprecation")
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}