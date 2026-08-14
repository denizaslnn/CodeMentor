package com.codementor.aiservice.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RedisStatusService {

    private final StringRedisTemplate redisTemplate;
    private static final String KEY_PREFIX = "task:";

    public RedisStatusService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void updateTaskStatus(String taskId, String status) {
        redisTemplate.opsForValue().set(KEY_PREFIX + taskId, status, Duration.ofHours(24));
    }
}
