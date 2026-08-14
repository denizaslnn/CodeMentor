package com.codementor.aiservice.dto;

import java.io.Serializable;

public class CodeTaskMessage implements Serializable {

    private String taskId;
    private String code;

    public CodeTaskMessage() {
    }

    public CodeTaskMessage(String taskId, String code) {
        this.taskId = taskId;
        this.code = code;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
