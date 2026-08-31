package com.codementor.codeservice.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "{validation.username.required}") String username,
        @NotBlank(message = "{validation.password.required}") String password
) {
}