package com.codementor.aiservice.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisStatusService {

    private final RedisTemplate<String, String> redisTemplate;
    private static final String KEY_PREFIX = "task:";

    public RedisStatusService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void updateStatus(String taskId, String status) {
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + taskId, status);
        } catch (Exception e) {
            System.err.println("⚠️ [ai-service] Redis güncelleme hatası (taskId=" + taskId + "): " + e.getMessage());
        }
    }

    public void updateCompleted(String taskId, String result) {
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + taskId, "COMPLETED:" + result);
        } catch (Exception e) {
            System.err.println("⚠️ [ai-service] Redis güncelleme hatası (taskId=" + taskId + "): " + e.getMessage());
        }
    }
}