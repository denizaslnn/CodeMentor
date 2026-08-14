package com.codementor.aiservice.consumer;

import com.codementor.aiservice.dto.CodeTaskMessage;
import com.codementor.aiservice.service.RedisStatusService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class CodeTaskConsumer {

    private final RedisStatusService redisStatusService;

    public CodeTaskConsumer(RedisStatusService redisStatusService) {
        this.redisStatusService = redisStatusService;
    }

    @RabbitListener(queues = "${rabbitmq.queue.name}")
    public void consumeMessage(CodeTaskMessage message) {
        System.out.println("📩 [ai-service] Kuyruktan mesaj alındı! Task ID: " + message.getTaskId());

        // 1. Durumu "PROCESSING" olarak güncelle
        redisStatusService.updateTaskStatus(message.getTaskId(), "PROCESSING");

        // 2. Yapay Zeka analiz simülasyonu (Gerçek LLM entegrasyonu öncesi 3 sn gecikme)
        try {
            Thread.sleep(3000); // Yapay zeka kodu analiz ediyor gibi simüle ediyoruz
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 3. Analiz bitti! Durumu Redis'e "COMPLETED" olarak yaz
        redisStatusService.updateTaskStatus(message.getTaskId(), "COMPLETED - Kod analizi başarıyla tamamlandı.");

        System.out.println("✅ [ai-service] Analiz tamamlandı. Task ID: " + message.getTaskId());
    }
}
