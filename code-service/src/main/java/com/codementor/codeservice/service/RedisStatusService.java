package com.codementor.codeservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis task status/result read-model.
 * <p>
 * REDIS SOURCE OF TRUTH DEĞİLDİR: PostgreSQL kalıcı source of truth,
 * Redis hızlı status/result erişimi için TTL'li bir cache/read modeldir.
 * Redis erişilemezse / anahtar TTL dolarsa PostgreSQL fallback kullanılır.
 * TTL'ler configuration üzerinden yönetilir ({@code app.redis.*}).
 */
@Service
@Slf4j
public class RedisStatusService {

    private static final String KEY_PREFIX = "task:";
    private static final String RESULT_SUFFIX = ":result";

    private final StringRedisTemplate redisTemplate;
    private final Duration taskStatusTtl;
    private final Duration taskResultTtl;

    public RedisStatusService(StringRedisTemplate redisTemplate,
                              @Value("${app.redis.task-status-ttl:PT24H}") Duration taskStatusTtl,
                              @Value("${app.redis.task-result-ttl:PT24H}") Duration taskResultTtl) {
        this.redisTemplate = redisTemplate;
        this.taskStatusTtl = taskStatusTtl;
        this.taskResultTtl = taskResultTtl;
    }

    // Görev durumunu Redis'e kaydet (TTL config'den)
    public void saveTaskStatus(String taskId, String status) {
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + taskId, status, taskStatusTtl);
        } catch (Exception e) {
            // Redis erişilemezse akış bozulmamalı; PostgreSQL source of truth olarak devam eder.
            log.error("Redis task status kaydı başarısız. taskId={}, status={}, error={}", taskId, status, e.getMessage(), e);
        }
    }

    // Görev durumunu Redis'ten getir (yoksa/hata olursa null -> PostgreSQL fallback)
    public String getTaskStatus(String taskId) {
        try {
            return redisTemplate.opsForValue().get(KEY_PREFIX + taskId);
        } catch (Exception e) {
            log.error("Redis task status okuması başarısız (PostgreSQL fallback kullanılacak). taskId={}, error={}", taskId, e.getMessage(), e);
            return null;
        }
    }

    public String getTaskResult(String taskId) {
        try {
            return redisTemplate.opsForValue().get(KEY_PREFIX + taskId + RESULT_SUFFIX);
        } catch (Exception e) {
            log.error("Redis task result okuması başarısız. taskId={}, error={}", taskId, e.getMessage(), e);
            return null;
        }
    }
}
