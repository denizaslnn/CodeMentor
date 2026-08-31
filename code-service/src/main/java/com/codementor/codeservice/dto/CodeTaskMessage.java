package com.codementor.codeservice.dto;

/**
 * RabbitMQ message contract: code-service → ai-service.
 * <p>
 * JSON serialization kullanılır (Jackson2JsonMessageConverter); Java
 * serialization'a dayanmaz. Her iki servis bu alan adları üzerinde anlaşır:
 *
 * <ul>
 *   <li>{@code taskId} — analiz task'ının benzersiz kimliği (idempotency anahtarı)</li>
 *   <li>{@code sourceCode} — analiz edilecek kaynak kod</li>
 *   <li>{@code prompt} — kullanıcı promptu (opsiyonel olabilir)</li>
 *   <li>{@code language} — kaynak kodun dili (opsiyonel; şimdilik null gönderilir)</li>
 * </ul>
 */
public class CodeTaskMessage {

    private String taskId;
    private String sourceCode;
    private String prompt;
    private String language;

    public CodeTaskMessage() {
    }

    public CodeTaskMessage(String taskId, String sourceCode, String prompt) {
        this.taskId = taskId;
        this.sourceCode = sourceCode;
        this.prompt = prompt;
    }

    public CodeTaskMessage(String taskId, String sourceCode, String prompt, String language) {
        this.taskId = taskId;
        this.sourceCode = sourceCode;
        this.prompt = prompt;
        this.language = language;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
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
                ", sourceCode='" + (sourceCode != null ? (sourceCode.length() > 30 ? sourceCode.substring(0, 30) + "..." : sourceCode) : null) + '\'' +
                ", prompt='" + (prompt != null ? (prompt.length() > 30 ? prompt.substring(0, 30) + "..." : prompt) : null) + '\'' +
                ", language='" + language + '\'' +
                '}';
    }
}