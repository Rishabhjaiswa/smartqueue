package com.smartqueue.backend.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

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
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public boolean exists(String correlationId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + correlationId));
    }

    public <T> void store(String correlationId, T result) {
        store(correlationId, result, DEFAULT_TTL);
    }

    public <T> void store(String correlationId, T result, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(result);
            redisTemplate.opsForValue().set(PREFIX + correlationId, json, ttl);
        } catch (JsonProcessingException e) {
            // Non-fatal: worst case is a duplicate on Kafka redelivery.
            // Better to continue than to block the patient.
            log.warn("Failed to store idempotency result for correlationId={}: {}", correlationId, e.getMessage());
        }
    }

    public <T> T getResult(String correlationId, Class<T> type) {
        String raw = redisTemplate.opsForValue().get(PREFIX + correlationId);
        if (raw == null) return null;
        try {
            return objectMapper.readValue(raw, type);
        } catch (Exception e) {
            log.warn("Failed to deserialise idempotency result for correlationId={}", correlationId);
            return null;
        }
    }
}
