package com.codementor.aiservice.service;

import com.codementor.aiservice.dto.CodeTaskMessage;
import com.codementor.aiservice.entity.AnalysisRequest;
import com.codementor.aiservice.repository.AnalysisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Covers the idempotent, at-least-once-safe processing logic:
 *   - blank/absent taskId, missing DB row -> no-op
 *   - already COMPLETED / FAILED -> no-op (idempotency)
 *   - PROCESSING (stuck after crash) -> reprocessed
 *   - success -> COMPLETED + aiResponse persisted
 *   - engine failure -> FAILED persisted + exception swallowed (no poison retry)
 *
 * <p>Status progression is verified through the Redis mock (which carries the
 * literal status strings), because {@link AnalysisRequest} is a mutable entity
 * mutated in place across the two {@code save()} calls and therefore the same
 * reference is captured by any argument captor.
 */
@ExtendWith(MockitoExtension.class)
class CodeTaskProcessingServiceTest {

    @Mock
    private AnalysisRepository repository;
    @Mock
    private RedisStatusService redis;
    @Mock
    private CodeAnalysisEngine engine;

    private CodeTaskProcessingService service;

    @BeforeEach
    void setUp() {
        service = new CodeTaskProcessingService(repository, redis, engine);
    }

    private AnalysisRequest request(String id, String status) {
        AnalysisRequest r = new AnalysisRequest();
        r.setId(id);
        r.setStatus(status);
        return r;
    }

    @Test
    void process_nullOrBlankTaskId_isNoOp() {
        service.process(new CodeTaskMessage(null, "src", "prompt", "java"));
        service.process(new CodeTaskMessage("   ", "src", "prompt", "java"));

        verifyNoInteractions(repository, redis, engine);
    }

    @Test
    void process_taskNotFound_isNoOp() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        service.process(new CodeTaskMessage("missing", "src", "prompt", "java"));

        verify(redis, never()).updateStatus(any(), any());
        verify(repository, never()).save(any());
        verifyNoInteractions(engine);
    }

    @Test
    void process_alreadyCompleted_isNoOp() {
        AnalysisRequest r = request("t1", "COMPLETED");
        when(repository.findById("t1")).thenReturn(Optional.of(r));

        service.process(new CodeTaskMessage("t1", "src", "prompt", "java"));

        verify(redis, never()).updateStatus(any(), any());
        verify(repository, never()).save(any());
        verifyNoInteractions(engine);
    }

    @Test
    void process_alreadyFailed_isNoOp() {
        AnalysisRequest r = request("t1", "FAILED");
        when(repository.findById("t1")).thenReturn(Optional.of(r));

        service.process(new CodeTaskMessage("t1", "src", "prompt", "java"));

        verifyNoInteractions(engine);
        verify(repository, never()).save(any());
    }

    @Test
    void process_stuckProcessing_isReprocessed() {
        AnalysisRequest r = request("t1", "PROCESSING");
        when(repository.findById("t1")).thenReturn(Optional.of(r));
        when(engine.analyze("src", "prompt")).thenReturn("analysed");

        service.process(new CodeTaskMessage("t1", "src", "prompt", "java"));

        // A crashed mid-flight PROCESSING task is safe to reprocess.
        verify(engine).analyze("src", "prompt");
    }

    @Test
    void process_success_marksCompletedAndWritesResult() {
        AnalysisRequest r = request("t1", "PENDING");
        when(repository.findById("t1")).thenReturn(Optional.of(r));
        when(engine.analyze("src", "prompt")).thenReturn("analysed");

        service.process(new CodeTaskMessage("t1", "src", "prompt", "java"));

        InOrder order = inOrder(redis, repository);
        order.verify(redis).updateStatus("t1", "PROCESSING");
        order.verify(repository).save(r);          // PROCESSING save
        verify(engine).analyze("src", "prompt");  // uses the new contract fields
        order.verify(redis).updateCompleted("t1", "analysed");
        order.verify(repository).save(r);          // COMPLETED save

        // The entity object is mutated in place; assert its final persisted state.
        assertThat(r.getStatus()).isEqualTo("COMPLETED");
        assertThat(r.getAiResponse()).isEqualTo("analysed");
    }

    @Test
    void process_analysisThrows_persistsFailedAndSwallowsException() {
        AnalysisRequest r = request("t1", "PENDING");
        when(repository.findById("t1")).thenReturn(Optional.of(r));
        when(engine.analyze("src", "prompt")).thenThrow(new RuntimeException("boom"));

        // Must not propagate: a failing task is ACK'd so it is not re-queued.
        service.process(new CodeTaskMessage("t1", "src", "prompt", "java"));

        InOrder order = inOrder(redis, repository);
        order.verify(redis).updateStatus("t1", "PROCESSING");
        order.verify(repository).save(r);          // PROCESSING save
        verify(engine).analyze("src", "prompt");
        order.verify(redis).updateStatus("t1", "FAILED");
        order.verify(repository).save(r);          // FAILED save
        verify(redis, never()).updateCompleted(any(), any());

        assertThat(r.getStatus()).isEqualTo("FAILED");
    }
}
