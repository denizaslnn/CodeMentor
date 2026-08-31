package com.codementor.aiservice.consumer;

import com.codementor.aiservice.dto.CodeTaskMessage;
import com.codementor.aiservice.service.CodeTaskProcessingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

/**
 * Consumer boundary tests. The consumer is now a thin delegator to
 * {@link CodeTaskProcessingService}; these tests lock that contract.
 */
@ExtendWith(MockitoExtension.class)
class CodeTaskConsumerTest {

    @Mock
    private CodeTaskProcessingService processingService;

    @Test
    void consumeMessage_delegatesToProcessor_withNewContractFields() {
        CodeTaskConsumer consumer = new CodeTaskConsumer(processingService);
        CodeTaskMessage message = new CodeTaskMessage("task-1", "int x = 1;", "lütfen kodu incele", "java");

        consumer.consumeMessage(message);

        // The consumer forwards the whole message; the processor (not the
        // consumer) decides how to read sourceCode/prompt/language.
        verify(processingService).process(message);
    }

    @Test
    void consumeMessage_ignoresNullMessage() {
        CodeTaskConsumer consumer = new CodeTaskConsumer(processingService);

        consumer.consumeMessage(null);

        verifyNoInteractions(processingService);
    }

    @Test
    void consumeMessage_delegatesEvenForBlankTaskId_processorHandlesIt() {
        CodeTaskConsumer consumer = new CodeTaskConsumer(processingService);
        CodeTaskMessage message = new CodeTaskMessage("  ", "int x = 1;", "prompt", "java");

        consumer.consumeMessage(message);

        // Blank-taskId guard lives in the processor; the consumer does not
        // pre-filter and still delegates.
        verify(processingService).process(message);
    }
}
