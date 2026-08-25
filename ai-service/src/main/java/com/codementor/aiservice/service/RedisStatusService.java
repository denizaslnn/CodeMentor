package com.codementor.aiservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
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
            log.error("Redis durum güncellemesi başarısız. taskId={}, status={}, error={}", taskId, status, e.getMessage(), e);
        }
    }

    public void updateCompleted(String taskId, String result) {
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + taskId, "COMPLETED:" + result);
        } catch (Exception e) {
            log.error("Redis tamamlanma sonucu kaydı başarısız. taskId={}, error={}", taskId, e.getMessage(), e);
        }
    }
}