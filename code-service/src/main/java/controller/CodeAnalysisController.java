package com.codementor.codeservice.controller;

import com.codementor.codeservice.CodeTaskMessage;
import com.codementor.codeservice.CodeTaskProducer;
import com.codementor.codeservice.service.RedisStatusService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class CodeAnalysisController {

    private final CodeTaskProducer taskProducer;
    private final RedisStatusService redisStatusService;

    public CodeAnalysisController(CodeTaskProducer taskProducer, RedisStatusService redisStatusService) {
        this.taskProducer = taskProducer;
        this.redisStatusService = redisStatusService;
    }

    @GetMapping("/test")
    public String test() {
        return "Code Service tıkır tıkır çalışıyor!";
    }

    // Eskisi: @PostMapping("/analyze")
    // Yenisi (Hem Chrome linkini hem Postman POST'unu destekler):
    @RequestMapping(value = "/analyze", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Map<String, String>> analyzeCode(@RequestParam String code) {
        String taskId = UUID.randomUUID().toString();

        // Durumu Redis'e kaydet
        redisStatusService.saveTaskStatus(taskId, "PENDING");
        
        // Mesajı RabbitMQ'ya gönder
        taskProducer.sendTask(taskId, code);

        Map<String, String> response = new HashMap<>();
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
