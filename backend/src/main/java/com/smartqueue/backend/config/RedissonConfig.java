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

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
              .setAddress("redis://" + redisHost + ":" + redisPort)
              // keep-alive so locks are not lost on idle connection reaping
              .setKeepAlive(true)
              // connection pool tuned for lock-only workload (small pool is fine)
              .setConnectionMinimumIdleSize(2)
              .setConnectionPoolSize(4);
        return Redisson.create(config);
    }
}
