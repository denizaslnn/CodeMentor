package com.codementor.codeservice;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CodeTaskProducer {

    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.exchange}")
    private String exchange;

    @Value("${rabbitmq.routingkey}")
    private String routingKey;

    public CodeTaskProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void sendTaskToQueue(CodeTaskMessage taskMessage) {
        rabbitTemplate.convertAndSend(exchange, routingKey, taskMessage);
        System.out.println(" [x] Görev RabbitMQ kuyruğuna fırlatıldı ID: " + taskMessage.getTaskId());
    }

    // Yeni yardımcı metod: controller'dan taskId ve code alıp mesaj oluşturur ve kuyruğa gönderir
    public void sendTask(String taskId, String code) {
        CodeTaskMessage message = new CodeTaskMessage(taskId, code, "java");
        sendTaskToQueue(message);
    }
}