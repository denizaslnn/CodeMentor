package com.codementor.codeservice.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RedisStatusService {

    private final StringRedisTemplate redisTemplate;
    private static final String KEY_PREFIX = "task:";
    private static final String RESULT_SUFFIX = ":result";

    public RedisStatusService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // Görev durumunu Redis'e kaydet (24 saat sonra otomatik silinsin)
    public void saveTaskStatus(String taskId, String status) {
        redisTemplate.opsForValue().set(KEY_PREFIX + taskId, status, Duration.ofHours(24));
    }

    // Görev durumunu Redis'ten getir
    public String getTaskStatus(String taskId) {
        return redisTemplate.opsForValue().get(KEY_PREFIX + taskId);
    }

    public String getTaskResult(String taskId) {
        return redisTemplate.opsForValue().get(KEY_PREFIX + taskId + RESULT_SUFFIX);
    }
}
