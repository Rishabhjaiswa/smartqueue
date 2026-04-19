package com.smartqueue.backend.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Micrometer metrics configuration — Phase 1 + Phase 2.
 *
 * Common tags applied to every metric:
 *   application=smartqueue-backend, environment=local|prod
 *
 * Registered metrics (by service):
 *   smartqueue.token.generated         - counter  (serviceType, priorityFlag)
 *   smartqueue.token.auto_expired      - counter  ()
 *   smartqueue.token.starvation_boost  - counter  ()           ← Phase 2
 *   smartqueue.ai.process              - timer    ()
 *   smartqueue.ai.process.async        - timer    ()           ← Phase 2
 *   smartqueue.ollama.call             - timer    ()
 *   smartqueue.queue.wait_time         - timer    (doctorId, serviceType) ← Phase 2
 *   smartqueue.redis.lock.wait         - timer    (lockKey)
 *   smartqueue.redis.lock.timeout      - counter  (lockKey)
 *   smartqueue.redis.lock.skipped      - counter  (lockKey)
 *
 * SLO buckets configured below let Prometheus compute exact percentile
 * histograms (P50/P95/P99) without client-side approximation.
 *
 * Prometheus scrape endpoint: GET /actuator/prometheus
 * Recommended scrape interval: 15 s
 */
@Configuration
public class MetricsConfig {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> commonTags(
            @Value("${spring.application.name:smartqueue-backend}") String appName,
            @Value("${management.metrics.tags.environment:local}") String env) {
        return registry -> registry.config()
                .commonTags(
                        "application", appName,
                        "environment", env
                )
                // ── SLO histogram buckets ────────────────────────────────────
                // ai.process / ollama.call: measure sub-second to 30 s range
                .meterFilter(sloFilter("smartqueue.ai",
                        Duration.ofMillis(100), Duration.ofMillis(500),
                        Duration.ofSeconds(1), Duration.ofSeconds(3),
                        Duration.ofSeconds(5), Duration.ofSeconds(10),
                        Duration.ofSeconds(20), Duration.ofSeconds(30)))
                // queue.wait_time: measure seconds to 60-minute range
                .meterFilter(sloFilter("smartqueue.queue",
                        Duration.ofSeconds(30), Duration.ofMinutes(1),
                        Duration.ofMinutes(2), Duration.ofMinutes(5),
                        Duration.ofMinutes(10), Duration.ofMinutes(20),
                        Duration.ofMinutes(30), Duration.ofMinutes(60)));
    }

    /**
     * Returns a {@link MeterFilter} that enables histogram publication and sets
     * explicit SLO bucket boundaries for all meters whose name starts with
     * {@code prefix}. This allows Prometheus to generate *_bucket time-series
     * for Grafana heatmaps and accurate percentile queries.
     */
    private MeterFilter sloFilter(String prefix, Duration... slos) {
        return new MeterFilter() {
            @Override
            public DistributionStatisticConfig configure(
                    io.micrometer.core.instrument.Meter.Id id,
                    DistributionStatisticConfig config) {
                if (id.getName().startsWith(prefix)) {
                    return DistributionStatisticConfig.builder()
                            .percentilesHistogram(true)
                            .serviceLevelObjectives(
                                    java.util.Arrays.stream(slos)
                                            .mapToDouble(d -> (double) d.toNanos())
                                            .toArray()
                            )
                            .build()
                            .merge(config);
                }
                return config;
            }
        };
    }
}
