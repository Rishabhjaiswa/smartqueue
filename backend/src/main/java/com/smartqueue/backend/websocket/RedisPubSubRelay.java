package com.smartqueue.backend.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Bridges Redis Pub/Sub → STOMP WebSocket sessions on this JVM instance.
 *
 * Problem solved:
 *   With multiple app instances behind a load balancer, a doctor's browser
 *   may be connected to instance-A while a queue mutation happens on instance-B.
 *   Direct SimpMessagingTemplate.convertAndSend() on instance-B never reaches
 *   the session on instance-A, so the browser never updates.
 *
 * Solution:
 *   1. WebSocketBroadcastService publishes to Redis channel "ws:topic:doctor:1"
 *   2. ALL running instances are subscribed to "ws:topic:*" via this listener
 *   3. Each instance relays the message to its LOCAL STOMP sessions
 *   => Every browser connected to any instance receives the update.
 *
 * Channel → STOMP topic mapping:
 *   ws:topic:doctor:{id}         →  /topic/doctor/{id}
 *   ws:topic:doctor:{id}:current →  /topic/doctor/{id}/current
 *   ws:topic:reception:overview  →  /topic/reception/overview
 *   ws:topic:admin:system        →  /topic/admin/system
 *
 * Registered in RedisConfig as a MessageListenerAdapter on pattern "ws:topic:*".
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedisPubSubRelay implements MessageListener {

    private static final String REDIS_CHANNEL_PREFIX = "ws:topic:";
    private static final String STOMP_TOPIC_PREFIX   = "/topic/";

    private final SimpMessagingTemplate stomp;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel());
        String body    = new String(message.getBody());

        if (!channel.startsWith(REDIS_CHANNEL_PREFIX)) {
            log.warn("RedisPubSubRelay received message on unexpected channel: {}", channel);
            return;
        }

        // e.g. "ws:topic:doctor:1" → "/topic/doctor/1"
        String stompDestination = STOMP_TOPIC_PREFIX
                + channel.substring(REDIS_CHANNEL_PREFIX.length()).replace(":", "/");

        log.debug("Relaying Redis Pub/Sub {} → STOMP {}", channel, stompDestination);

        try {
            // Body is already JSON — send raw string so no double-serialisation
            stomp.convertAndSend(stompDestination, body);
        } catch (Exception e) {
            log.error("Failed to relay Pub/Sub message to STOMP destination {}: {}",
                    stompDestination, e.getMessage());
        }
    }
}
