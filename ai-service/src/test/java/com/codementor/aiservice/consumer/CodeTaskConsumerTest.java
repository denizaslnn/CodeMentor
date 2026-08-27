package com.codementor.aiservice.consumer;

import com.codementor.aiservice.dto.CodeTaskMessage;
import com.codementor.aiservice.repository.AnalysisRepository;
import com.codementor.aiservice.service.AiAnalysisService;
import com.codementor.aiservice.service.RedisStatusService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class CodeTaskConsumerTest {

    @Mock
    private RedisStatusService redisStatusService;

    @Mock
    private AiAnalysisService aiAnalysisService;

    @Mock
    private AnalysisRepository analysisRepository;

    /**
     * Adım 9 doğrulaması: mesaj kuyruktan alındığıda,
     * task statüsünü anında Redis'te PROCESSING olarak güncellenmelidir.
     * (Adım 10'a ilgili analyze() çağrı sadece kuralar — bu testte,
     * analyze 3sn sın, sadece PROCESSING'e bakar.)
     */
    @Test
    void consumeMessage_updatesRedisStatusToProcessing() {
        CodeTaskConsumer consumer = new CodeTaskConsumer(
                redisStatusService, aiAnalysisService, analysisRepository);

        CodeTaskMessage message = new CodeTaskMessage("task-123", "int x = 1;", "java");

        consumer.consumeMessage(message);

        verify(redisStatusService).updateStatus("task-123", "PROCESSING");
        verify(aiAnalysisService).analyze("int x = 1;", "java");
    }

    /**
     * Adım 8 doğrulaması: boş/geçersiziz taskId'li mesajlar
     * Redis/DB'de dokunmadan işlenirilir.
     */
    @Test
    void consumeMessage_ignoresBlankTaskId() {
        CodeTaskConsumer consumer = new CodeTaskConsumer(
                redisStatusService, aiAnalysisService, analysisRepository);

        consumer.consumeMessage(new CodeTaskMessage("  ", "int x = 1;", "java"));

        verifyNoMoreInteractions(redisStatusService, aiAnalysisService);
    }

    /**
     * Adım 8 doğrulaması: null mesaj, atışlı işlenmeden lenirilir.
     */
    @Test
    void consumeMessage_ignoresNullMessage() {
        CodeTaskConsumer consumer = new CodeTaskConsumer(
                redisStatusService, aiAnalysisService, analysisRepository);

        consumer.consumeMessage(null);

        verifyNoMoreInteractions(redisStatusService, aiAnalysisService);
    }
}