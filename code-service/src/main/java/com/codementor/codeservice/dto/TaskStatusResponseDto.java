package com.codementor.codeservice.dto;

public record TaskStatusResponseDto(
        String taskId,
        String status,
        String result
) {
}
