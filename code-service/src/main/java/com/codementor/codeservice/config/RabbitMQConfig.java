package com.codementor.codeservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

   @Value("${rabbitmq.queue}")
   private String queueName;

   @Value("${rabbitmq.exchange}")
   private String exchangeName;

   @Value("${rabbitmq.routingkey}")
   private String routingKey;

   @Bean
   public Queue queue() {
       return new Queue(queueName, true);
   }

   @Bean
   public TopicExchange exchange() {
       return new TopicExchange(exchangeName);
   }

   @Bean
   public Binding binding(Queue queue, TopicExchange exchange) {
       return BindingBuilder.bind(queue).to(exchange).with(routingKey);
   }

   @Bean
   @SuppressWarnings("deprecation")
   public MessageConverter jsonMessageConverter() {
       return new Jackson2JsonMessageConverter();
   }
}