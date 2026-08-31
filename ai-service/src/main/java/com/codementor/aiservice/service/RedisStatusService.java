package com.codementor.aiservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis status/result read-model writer for ai-service.
 * <p>
 * Mirrors the key layout used by code-service's {@code RedisStatusService}
 * ({@code task:<id>} for status, {@code task:<id>:result} for the result) so
 * code-service / gateway can read results independently. TTL is configurable
 * via {@code app.redis.task-status-ttl} (default 24h) so stale results expire.
 */
@Service
@Slf4j
public class RedisStatusService {

    private static final String KEY_PREFIX = "task:";
    private static final String RESULT_SUFFIX = ":result";

    private final RedisTemplate<String, String> redisTemplate;
    private final Duration ttl;

    public RedisStatusService(RedisTemplate<String, String> redisTemplate,
                              @Value("${app.redis.task-status-ttl:PT24H}") Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.ttl = ttl;
    }

    public void updateStatus(String taskId, String status) {
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + taskId, status, ttl);
            redisTemplate.delete(KEY_PREFIX + taskId + RESULT_SUFFIX);
        } catch (Exception e) {
            log.error("Redis durum güncellemesi başarısız. taskId={}, status={}, error={}", taskId, status, e.getMessage(), e);
        }
    }

    public void updateCompleted(String taskId, String result) {
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + taskId, "COMPLETED", ttl);
            redisTemplate.opsForValue().set(KEY_PREFIX + taskId + RESULT_SUFFIX, result, ttl);
        } catch (Exception e) {
            log.error("Redis tamamlanma kaydı başarısız. taskId={}, error={}", taskId, e.getMessage(), e);
        }
    }
}
