package com.codementor.codeservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodeRequestDto {
    @NotBlank(message = "{validation.sourcecode.required}")
    @Size(max = 10000, message = "{validation.sourcecode.toolong}")
    private String sourceCode;

    private String prompt;
}
