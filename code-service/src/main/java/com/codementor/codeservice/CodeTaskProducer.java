package com.codementor.codeservice;

import com.codementor.codeservice.event.TaskCreatedEvent;
import com.codementor.codeservice.publisher.TaskEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class CodeTaskProducer {

    private final TaskEventPublisher taskEventPublisher;

    public CodeTaskProducer(TaskEventPublisher taskEventPublisher) {
        this.taskEventPublisher = taskEventPublisher;
    }

    public void sendTask(String taskId, String code) {
        taskEventPublisher.publishTaskCreated(new TaskCreatedEvent(taskId, code, "java"));
    }
}