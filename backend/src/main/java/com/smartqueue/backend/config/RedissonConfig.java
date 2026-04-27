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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Configuration
@ConditionalOnProperty(name = "spring.data.redis.url")
public class RedissonConfig {

    @Value("${spring.data.redis.url}")
    private String redisUrl;

    @Value("${REDIS_PASSWORD:}")
    private String redisPassword;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        org.redisson.config.SingleServerConfig serverConfig = config.useSingleServer()
                .setAddress(redisUrl)
                .setTimeout(5000)
                .setRetryAttempts(5)
                .setRetryInterval(2000)
                .setPingConnectionInterval(2000)
                .setKeepAlive(true)
                .setConnectionMinimumIdleSize(2)
                .setConnectionPoolSize(10);

        if (redisPassword != null && !redisPassword.isEmpty()) {
            serverConfig.setPassword(redisPassword);
        }

        if (redisUrl.startsWith("rediss://")) {
            serverConfig.setSslEnableEndpointIdentification(false);
        }

        return Redisson.create(config);
    }
}
