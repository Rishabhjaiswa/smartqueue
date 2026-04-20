package com.smartqueue.backend.config;

import com.smartqueue.backend.websocket.RedisPubSubRelay;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Redis configuration for SmartQueue.
 *
 * Responsibilities:
 *  1. RedisTemplate<String,String> — existing ZSet + value operations (unchanged)
 *  2. RedisMessageListenerContainer — subscribes to "ws:topic:*" pattern so
 *     RedisPubSubRelay can broadcast to local STOMP sessions on this instance.
 *     This is the fix for WebSocket fan-out across multiple JVM instances.
 */
@Configuration
@ConditionalOnProperty(name = "REDIS_URL")
public class RedisConfig {

    // ── 1. RedisTemplate (unchanged behaviour) ─────────────────────────────────

    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        return template;
    }

    // ── 2. Pub/Sub listener container (new: enables horizontal WS scaling) ─────

    /**
     * Adapts RedisPubSubRelay to the Spring MessageListener interface.
     * The method name "onMessage" must match RedisPubSubRelay.onMessage().
     */
    @Bean
    public MessageListenerAdapter pubSubListenerAdapter(RedisPubSubRelay relay) {
        return new MessageListenerAdapter(relay, "onMessage");
    }

    /**
     * Subscribes to ALL ws:topic:* channels using a pattern subscription.
     * Every running instance gets its own subscription, so every instance
     * relays the message to its locally connected STOMP clients.
     *
     * Pattern "ws:topic:*" covers:
     *   ws:topic:doctor:1
     *   ws:topic:doctor:1:current
     *   ws:topic:reception:overview
     *   ws:topic:admin:system
     */
    @Bean
    public RedisMessageListenerContainer redisListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter pubSubListenerAdapter) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(pubSubListenerAdapter, new PatternTopic("ws:topic:*"));
        return container;
    }
}
