package com.smartqueue.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartqueue.backend.dto.DoctorQueueDTO;
import com.smartqueue.backend.dto.ReceptionOverviewDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * WebSocket broadcast service — now routes all messages through Redis Pub/Sub.
 *
 * Why: When multiple JVM instances run behind a load balancer, a STOMP message
 * sent on Instance A is only delivered to clients connected to Instance A.
 * Publishing to Redis ensures every instance's RedisPubSubRelay picks up the
 * payload and forwards it to its own locally-connected STOMP clients.
 *
 * Channel naming: ws:topic:<stomp-destination-path-with-colons>
 *   e.g. /topic/doctor/1  →  ws:topic:doctor:1
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketBroadcastService {

    private final Optional<RedisTemplate<String, String>> redisTemplate;
    private final ObjectMapper objectMapper;

    // ── Channel-name helpers ─────────────────────────────────────────────────

    private static final String WS_PREFIX = "ws:topic:";

    // ── Broadcast methods ────────────────────────────────────────────────────

    /** Pushes a full doctor-queue snapshot to all instances. */
    public void broadcastDoctorQueue(Long doctorId, DoctorQueueDTO dto) {
        publish(WS_PREFIX + "doctor:" + doctorId, dto);
    }

    /** Pushes the current-token display update for a specific doctor's screen. */
    public void broadcastCurrentToken(Long doctorId, String tokenNumber) {
        publish(WS_PREFIX + "doctor:" + doctorId + ":current", tokenNumber);
    }

    /** Pushes a reception dashboard snapshot to all connected reception clients. */
    public void broadcastReceptionOverview(ReceptionOverviewDTO dto) {
        publish(WS_PREFIX + "reception:overview", dto);
    }

    /** Sends a targeted notification to a single patient via user-destination. */
    public void notifyPatient(Long patientId, String message) {
        publish(WS_PREFIX + "patient:" + patientId,
                Map.of("message", message, "timestamp", LocalDateTime.now().toString()));
    }

    /** Sends a system alert to all admin clients. */
    public void broadcastAdminAlert(String alert) {
        publish(WS_PREFIX + "admin:system",
                Map.of("alert", alert, "time", LocalDateTime.now().toString()));
    }

    // ── Internal Pub/Sub publisher ────────────────────────────────────────────

    private void publish(String channel, Object payload) {
        if (redisTemplate.isEmpty()) {
            log.warn("Redis disabled: Bypassing WebSocket broadcast to {}", channel);
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            redisTemplate.get().convertAndSend(channel, json);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize WebSocket payload for channel {}: {}", channel, e.getMessage());
        }
    }
}