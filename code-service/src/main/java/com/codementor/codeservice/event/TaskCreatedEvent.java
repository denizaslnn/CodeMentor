package com.codementor.codeservice.event;

public class TaskCreatedEvent {
    private final String taskId;
    private final String sourceCode;
    private final String prompt;

    public TaskCreatedEvent(String taskId, String sourceCode, String prompt) {
        this.taskId = taskId;
        this.sourceCode = sourceCode;
        this.prompt = prompt;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public String getPrompt() {
        return prompt;
    }
}
