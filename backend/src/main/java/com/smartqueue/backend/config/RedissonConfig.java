package com.smartqueue.backend.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides a RedissonClient bean for distributed locking.
 *
 * Uses the same Redis instance already configured for spring-data-redis;
 * Redisson operates independently on its own connection pool so there is
 * no conflict with the existing RedisTemplate.
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("rediss://" + redisHost + ":" + redisPort)
                .setPassword(redisPassword)
                .setKeepAlive(true)
                .setConnectionMinimumIdleSize(2)
                .setConnectionPoolSize(4);
        return Redisson.create(config);
    }
}
