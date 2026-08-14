package com.codementor.codeservice;

import java.io.Serializable;

public class CodeTaskMessage implements Serializable {
    private String taskId;
    private String code;
    private String language;

    public CodeTaskMessage() {
    }

    public CodeTaskMessage(String taskId, String code, String language) {
        this.taskId = taskId;
        this.code = code;
        this.language = language;
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

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    @Override
    public String toString() {
        return "CodeTaskMessage{" +
                "taskId='" + taskId + '\'' +
                ", language='" + language + '\'' +
                ", code='" + (code != null ? (code.length()>30?code.substring(0,30)+"...":code) : null) + '\'' +
                '}';
    }
}