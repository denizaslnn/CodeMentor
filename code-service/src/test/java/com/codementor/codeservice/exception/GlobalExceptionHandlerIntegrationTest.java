package com.codementor.codeservice.exception;

import com.codementor.codeservice.controller.CodeAnalysisController;
import com.codementor.codeservice.dto.ApiResponse;
import com.codementor.codeservice.dto.CodeRequestDto;
import com.codementor.codeservice.service.CodeAnalysisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CodeAnalysisController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, com.codementor.codeservice.config.LocaleConfig.class, com.codementor.codeservice.config.ApiResponseAdvice.class})
class GlobalExceptionHandlerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CodeAnalysisService codeAnalysisService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void whenTaskNotFound_returnsStandardErrorResponse_InTurkish() throws Exception {
        when(codeAnalysisService.getTaskStatus("invalid-id"))
                .thenThrow(new TaskNotFoundException("invalid-id"));

        mockMvc.perform(get("/api/v1/status/invalid-id")
                        .header("Accept-Language", "tr"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("TASK_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Görev bulunamadı: invalid-id"));
    }

    @Test
    void whenTaskNotFound_returnsStandardErrorResponse_InEnglish() throws Exception {
        when(codeAnalysisService.getTaskStatus("invalid-id"))
                .thenThrow(new TaskNotFoundException("invalid-id"));

        mockMvc.perform(get("/api/v1/status/invalid-id")
                        .header("Accept-Language", "en"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("TASK_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Analysis task not found: invalid-id"));
    }

    @Test
    void whenValidationFails_returnsStandardErrorResponse() throws Exception {
        CodeRequestDto invalidRequest = new CodeRequestDto("", ""); // sourceCode empty

        mockMvc.perform(post("/api/v1/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void whenUnexpectedException_returnsStandardErrorResponse() throws Exception {
        when(codeAnalysisService.getTaskStatus("any-id"))
                .thenThrow(new RuntimeException("Something went wrong"));

        mockMvc.perform(get("/api/v1/status/any-id"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").exists());
    }
}
