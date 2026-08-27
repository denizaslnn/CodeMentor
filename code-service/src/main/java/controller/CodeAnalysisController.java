package com.codementor.codeservice.controller;

import com.codementor.codeservice.dto.CodeRequestDto;
import com.codementor.codeservice.dto.TaskStatusResponseDto;
import com.codementor.codeservice.service.CodeAnalysisService;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<Map<String, Object>> analyzeCode(@RequestBody CodeRequestDto requestDto) {
        String taskId = codeAnalysisService.initiateAnalysis(requestDto);

        Map<String, Object> response = new HashMap<>();
        response.put("taskId", taskId);
        response.put("status", "PENDING");
        response.put("message", "Kod analiz görevi kuyruğa başarıyla eklendi.");

        return ResponseEntity.status(202).body(response);
    }

    @GetMapping("/status/{taskId}")
    public ResponseEntity<TaskStatusResponseDto> getStatus(@PathVariable String taskId) {
        return ResponseEntity.ok(codeAnalysisService.getTaskStatus(taskId));
    }
}