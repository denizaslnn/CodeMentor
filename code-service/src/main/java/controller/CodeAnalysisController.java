package com.codementor.codeservice.controller;

import com.codementor.codeservice.dto.CodeRequestDto;
import com.codementor.codeservice.service.CodeAnalysisService;
import com.codementor.codeservice.service.RedisStatusService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class CodeAnalysisController {

    private final CodeAnalysisService codeAnalysisService;
    private final RedisStatusService redisStatusService;

    public CodeAnalysisController(CodeAnalysisService codeAnalysisService,
                                  RedisStatusService redisStatusService) {
        this.codeAnalysisService = codeAnalysisService;
        this.redisStatusService = redisStatusService;
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
    public ResponseEntity<Map<String, String>> getStatus(@PathVariable String taskId) {
        String status = redisStatusService.getTaskStatus(taskId);

        if (status == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Görev bulunamadı veya süresi dolmuş."));
        }

        Map<String, String> response = new HashMap<>();
        response.put("taskId", taskId);
        response.put("status", status);

        return ResponseEntity.ok(response);
    }
}