package com.codementor.codeservice.exception;

public class TaskNotFoundException extends AppException {
    public TaskNotFoundException(String taskId) {
        super("error.task.notfound", "TASK_NOT_FOUND", taskId);
    }
}
