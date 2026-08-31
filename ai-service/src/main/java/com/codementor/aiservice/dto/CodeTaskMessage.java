package com.codementor.aiservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * RabbitMQ message contract: code-service -> ai-service.
 * <p>
 * Structurally mirrors {@code com.codementor.codeservice.dto.CodeTaskMessage}
 * (same JSON field names). The previous contract was buggy: it leaked the user
 * prompt into the {@code language} field and stored the code under {@code code}.
 * This fixed contract uses explicit {@code sourceCode}/{@code prompt}/
 * {@code language} fields so a real analyser engine can use the language.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CodeTaskMessage {

    private String taskId;
    private String sourceCode;
    private String prompt;
    private String language;

    @Override
    public String toString() {
        return "CodeTaskMessage{taskId='" + taskId + '\''
                + ", sourceCode='" + truncate(sourceCode) + '\''
                + ", prompt='" + truncate(prompt) + '\''
                + ", language='" + language + '\''
                + '}';
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > 30 ? s.substring(0, 30) + "..." : s;
    }
}
