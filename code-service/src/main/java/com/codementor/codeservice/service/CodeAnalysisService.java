package com.codementor.codeservice.service;

import com.codementor.codeservice.CodeTaskMessage;
import com.codementor.codeservice.dto.CodeRequestDto;
import com.codementor.codeservice.entity.AnalysisRequest;
import com.codementor.codeservice.repository.AnalysisRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.codementor.codeservice.publisher.TaskEventPublisher;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class CodeAnalysisService {

    private final AnalysisRepository repository;
    private final TaskEventPublisher taskEventPublisher;

    public CodeAnalysisService(AnalysisRepository repository,
                               TaskEventPublisher taskEventPublisher) {
        this.repository = repository;
        this.taskEventPublisher = taskEventPublisher;
    }

    @Transactional
    public String initiateAnalysis(CodeRequestDto requestDto) {
        String taskId = UUID.randomUUID().toString();

        AnalysisRequest request = new AnalysisRequest();
        request.setId(taskId);
        request.setSourceCode(requestDto.getSourceCode());
        request.setPrompt(requestDto.getPrompt());
        request.setStatus("PENDING");
        request.setCreatedAt(LocalDateTime.now());

        repository.save(request);

        // Publish domain event; RabbitMqTaskPublisher will listen AFTER_COMMIT and publish to RabbitMQ/Redis
        taskEventPublisher.publishTaskCreated(new com.codementor.codeservice.event.TaskCreatedEvent(taskId, requestDto.getSourceCode(), requestDto.getPrompt()));

        return taskId;
    }
}
