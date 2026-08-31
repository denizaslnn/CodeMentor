package com.codementor.codeservice.service;

import com.codementor.codeservice.dto.CodeRequestDto;
import com.codementor.codeservice.dto.TaskStatusResponseDto;
import com.codementor.codeservice.entity.AnalysisRequest;
import com.codementor.codeservice.event.TaskCreatedEvent;
import com.codementor.codeservice.exception.TaskNotFoundException;
import com.codementor.codeservice.publisher.TaskEventPublisher;
import com.codementor.codeservice.repository.AnalysisRepository;
import com.github.f4b6a3.uuid.UuidCreator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class CodeAnalysisService {

    private final AnalysisRepository repository;
    private final TaskEventPublisher taskEventPublisher;
    private final RedisStatusService redisStatusService;

    public CodeAnalysisService(AnalysisRepository repository,
                               TaskEventPublisher taskEventPublisher,
                               RedisStatusService redisStatusService) {
        this.repository = repository;
        this.taskEventPublisher = taskEventPublisher;
        this.redisStatusService = redisStatusService;
    }

    @Transactional
    public String initiateAnalysis(CodeRequestDto requestDto) {
        String taskId = UuidCreator.getTimeOrdered().toString();

        AnalysisRequest request = new AnalysisRequest();
        request.setId(taskId);
        request.setSourceCode(requestDto.getSourceCode());
        request.setPrompt(requestDto.getPrompt());
        request.setStatus("PENDING");
        request.setCreatedAt(LocalDateTime.now());

        repository.save(request);

        // Publish domain event; RabbitMqTaskPublisher listens AFTER_COMMIT and
        // publishes to RabbitMQ + writes the Redis PENDING status.
        taskEventPublisher.publishTaskCreated(
                new TaskCreatedEvent(taskId, requestDto.getSourceCode(), requestDto.getPrompt()));

        return taskId;
    }

    public TaskStatusResponseDto getTaskStatus(String taskId) {
        String redisStatus = redisStatusService.getTaskStatus(taskId);
        if (redisStatus != null) {
            return toResponseFromRedis(taskId, redisStatus);
        }

        AnalysisRequest request = repository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException(taskId));

        return new TaskStatusResponseDto(taskId, request.getStatus(), request.getAiResponse());
    }

    private TaskStatusResponseDto toResponseFromRedis(String taskId, String redisStatusValue) {
        int separatorIndex = redisStatusValue.indexOf(':');
        if (separatorIndex > 0) {
            String status = redisStatusValue.substring(0, separatorIndex);
            String result = redisStatusValue.substring(separatorIndex + 1);
            return new TaskStatusResponseDto(taskId, status, result);
        }

        if ("COMPLETED".equals(redisStatusValue)) {
            String redisResult = redisStatusService.getTaskResult(taskId);
            if (redisResult != null) {
                return new TaskStatusResponseDto(taskId, redisStatusValue, redisResult);
            }
            AnalysisRequest request = repository.findById(taskId).orElse(null);
            return new TaskStatusResponseDto(taskId, redisStatusValue,
                    request != null ? request.getAiResponse() : null);
        }

        return new TaskStatusResponseDto(taskId, redisStatusValue, null);
    }
}
