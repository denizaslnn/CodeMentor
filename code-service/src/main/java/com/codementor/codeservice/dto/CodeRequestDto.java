package com.codementor.codeservice.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodeRequestDto {
    private String sourceCode;
    private String prompt;
}
