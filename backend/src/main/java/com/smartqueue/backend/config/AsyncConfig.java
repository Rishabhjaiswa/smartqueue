package com.smartqueue.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Async executor configuration for SmartQueue.
 *
 * Why a dedicated pool for AI triage?
 *   Ollama calls can block for 2–10 s (LLM inference). Without isolation,
 *   concurrent triage requests would exhaust Tomcat's thread pool and cause
 *   HTTP 503 errors for unrelated endpoints (token status, reception view, etc.).
 *
 * Design decisions:
 *   - corePoolSize  = 4   → always available for bursty triage traffic
 *   - maxPoolSize   = 16  → scales up under sustained load
 *   - queueCapacity = 50  → bounds memory: beyond 50 queued tasks the
 *     CallerRunsPolicy executes on the caller's HTTP thread (backpressure)
 *   - keepAlive     = 60s → idle threads beyond core are released after 1 min
 *   - waitForJobsOnShutdown = true → graceful drain on SIGTERM
 *   - awaitTerminationSeconds = 30 → max wait before forcible shutdown
 *
 * Metrics: the executor exposes active/pool/queue size via Micrometer's
 * ExecutorServiceMetrics (auto-registered through Spring Boot Actuator).
 */
@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig {

    @Value("${smartqueue.async.ai.core-pool-size:4}")
    private int corePoolSize;

    @Value("${smartqueue.async.ai.max-pool-size:16}")
    private int maxPoolSize;

    @Value("${smartqueue.async.ai.queue-capacity:50}")
    private int queueCapacity;

    /**
     * Executor used by {@code @Async("aiTriageExecutor")} in AIService.
     * Named to avoid ambiguity with other executors (e.g. scheduled task executor).
     */
    @Bean(name = "aiTriageExecutor")
    public Executor aiTriageExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("ai-triage-");
        executor.setKeepAliveSeconds(60);
        executor.setAllowCoreThreadTimeOut(true);

        // Backpressure: if queue is full, run on calling thread rather than reject
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // Graceful shutdown: wait up to 30 s for in-flight triage tasks to finish
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();
        log.info("AI triage executor initialised: core={}, max={}, queue={}",
                corePoolSize, maxPoolSize, queueCapacity);
        return executor;
    }
}
