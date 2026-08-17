package com.codementor.codeservice.publisher;

import com.codementor.codeservice.event.TaskCreatedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class TaskEventPublisher {

    private final ApplicationEventPublisher publisher;

    public TaskEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void publishTaskCreated(TaskCreatedEvent event) {
        publisher.publishEvent(event);
    }
}
