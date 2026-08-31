package com.codementor.aiservice.config;

import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JSON message converter for AMQP.
 * <p>
 * INFERRED type-precedence: deserialization infers the target type from the
 * {@code @RabbitListener} parameter (CodeTaskMessage) rather than trusting a
 * client-controlled {@code __TypeId__} header - defense against Jackson type
 * injection / gadget-deserialization attacks. Messages are plain JSON produced
 * by code-service's matching converter.
 */
@Configuration
public class RabbitMQConfig {

    @Bean
    @SuppressWarnings("deprecation")
    public MessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
