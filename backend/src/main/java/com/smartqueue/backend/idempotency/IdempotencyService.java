package com.smartqueue.backend.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis-backed idempotency guard for token generation.
 *
 * Problem it solves:
 *   A Kafka consumer may reprocess a triage.completed event if the consumer
 *   crashes after generating a token but before committing the offset.
 *   Without idempotency, the patient gets two tokens in the queue.
 *
 * Strategy:
 *   key  = "idem:{correlationId}"   (correlationId set by API Gateway per request)
 *   value = JSON-serialised TokenResponse
 *   TTL   = 10 minutes (enough to cover any realistic Kafka redelivery window)
 *
 * Usage pattern (double-checked locking):
 *   1. Check outside lock  → if present, return cached result (fast path)
 *   2. Acquire Redisson lock
 *   3. Check again inside lock (another instance may have just created it)
 *   4. Execute business logic
 *   5. Store result + release lock
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private static final String PREFIX = "idem:";
    private static final Duration DEFAULT_TTL   = Duration.ofMinutes(10);
    public  static final Duration CHECKIN_TTL   = Duration.ofSeconds(60);

    private final Optional<RedisTemplate<String, String>> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.redis.required:false}")
    private boolean redisRequired;

    private void checkRedisRequired(String operation) {
        if (redisTemplate.isEmpty()) {
            if (redisRequired) {
                throw new IllegalStateException("Redis required for this operation: " + operation);
            }
            log.warn("Redis unavailable - falling back for operation: {}", operation);
        }
    }

    public boolean exists(String correlationId) {
        checkRedisRequired("idempotency-exists");
        if (redisTemplate.isEmpty()) return false;
        return Boolean.TRUE.equals(redisTemplate.get().hasKey(PREFIX + correlationId));
    }

    public <T> void store(String correlationId, T result) {
        store(correlationId, result, DEFAULT_TTL);
    }

    public <T> void store(String correlationId, T result, Duration ttl) {
        checkRedisRequired("idempotency-store");
        if (redisTemplate.isEmpty()) return;
        try {
            String json = objectMapper.writeValueAsString(result);
            redisTemplate.get().opsForValue().set(PREFIX + correlationId, json, ttl);
        } catch (JsonProcessingException e) {
            log.warn("Failed to store idempotency result for correlationId={}: {}", correlationId, e.getMessage());
        }
    }

    public <T> T getResult(String correlationId, Class<T> type) {
        checkRedisRequired("idempotency-getResult");
        if (redisTemplate.isEmpty()) return null;
        String raw = redisTemplate.get().opsForValue().get(PREFIX + correlationId);
        if (raw == null) return null;
        try {
            return objectMapper.readValue(raw, type);
        } catch (Exception e) {
            log.warn("Failed to deserialise idempotency result for correlationId={}", correlationId);
            return null;
        }
    }
    /**
     * Atomic SETNX guard — returns true only for the FIRST caller with this key.
     * All subsequent callers within the TTL window return false immediately.
     * Use this instead of exists()+store() to eliminate the TOCTOU race.
     *
     * @param key  idempotency key (e.g. "checkin:{hash}")
     * @param ttl  how long to block duplicate requests
     * @return true if this caller acquired the lock (proceed), false if duplicate
     */
    public boolean tryAcquire(String key, Duration ttl) {
        checkRedisRequired("idempotency-tryAcquire");
        if (redisTemplate.isEmpty()) return true; // Redis down → allow through
        Boolean acquired = redisTemplate.get().opsForValue()
                .setIfAbsent(PREFIX + key, "1", ttl);
        return Boolean.TRUE.equals(acquired);
    }
}
