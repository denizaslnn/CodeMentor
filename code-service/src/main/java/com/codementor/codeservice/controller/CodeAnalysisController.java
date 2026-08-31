package com.codementor.codeservice.controller;

import com.codementor.codeservice.dto.ApiResponse;
import com.codementor.codeservice.dto.CodeRequestDto;
import com.codementor.codeservice.dto.TaskStatusResponseDto;
import com.codementor.codeservice.service.CodeAnalysisService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class CodeAnalysisController {

    private final CodeAnalysisService codeAnalysisService;

    public CodeAnalysisController(CodeAnalysisService codeAnalysisService) {
        this.codeAnalysisService = codeAnalysisService;
    }

    @GetMapping("/test")
    public String test() {
        return "Code Service tıkır tıkır çalışıyor!";
    }

    @PostMapping("/analyze")
    public ApiResponse<Map<String, Object>> analyzeCode(@Valid @RequestBody CodeRequestDto requestDto) {
        String taskId = codeAnalysisService.initiateAnalysis(requestDto);

        Map<String, Object> response = new HashMap<>();
        response.put("taskId", taskId);
        response.put("status", "PENDING");

        return ApiResponse.success("success.analyze.queued", response);
    }

    @GetMapping("/status/{taskId}")
    public ApiResponse<TaskStatusResponseDto> getStatus(@PathVariable String taskId) {
        return ApiResponse.success(codeAnalysisService.getTaskStatus(taskId));
    }
}