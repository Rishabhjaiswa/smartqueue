package com.smartqueue.backend.service;

import com.smartqueue.backend.entity.Token;
import com.smartqueue.backend.enums.TokenStatus;
import com.smartqueue.backend.lock.RedissonLockService;
import com.smartqueue.backend.repository.TokenRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled housekeeping jobs.
 *
 * All jobs use a distributed leader lock (try-and-skip semantics) so that
 * in a multi-instance deployment only ONE instance processes each run.
 * The other instances will silently skip their trigger for that cycle.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClinicScheduledJobs {

    private final TokenRepository tokenRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final WebSocketBroadcastService broadcastService;
    private final DoctorQueueService doctorQueueService;
    private final RedissonLockService lockService;
    private final MeterRegistry meterRegistry;

    @Value("${clinic.no-show-timeout-minutes:10}")
    private int noShowTimeoutMins;

    @Value("${clinic.starvation-boost-after-minutes:30}")
    private int starvationBoostAfterMins;

    @Value("${clinic.starvation-score-reduction:500000}")
    private long starvationScoreReduction;

    private static final String QUEUE_KEY = "queue:doctor:";

    private Counter autoExpiredCounter;
    private Counter starvationBoostCounter;

    @PostConstruct
    private void initMetrics() {
        autoExpiredCounter = Counter.builder("smartqueue.token.auto_expired")
                .description("Tokens automatically expired due to no-show timeout")
                .register(meterRegistry);
        starvationBoostCounter = Counter.builder("smartqueue.token.starvation_boost")
                .description("Tokens that received a starvation priority boost")
                .register(meterRegistry);
    }

    // ── Scheduled Jobs ────────────────────────────────────────────────────────

    @Scheduled(fixedDelay = 60_000)
    public void autoExpireCalledTokens() {
        // Leader lock: lease = 55 s < interval (60 s) so it auto-releases if
        // this instance crashes before the run completes.
        lockService.executeWithLockIfAvailable("lock:job:auto-expire", 55, this::doExpireCalledTokens);
    }

    /**
     * Starvation prevention job — runs every 5 minutes.
     *
     * Problem: A WAITING token with high base priority (e.g. SPECIALIST)
     * may be repeatedly skipped if a stream of EMERGENCY tokens keeps arriving.
     * Over time, its wait grows unboundedly — "starvation".
     *
     * Fix: Every 5 min we scan WAITING tokens older than
     * {@code clinic.starvation-boost-after-minutes}. For each such token we
     * lower its Redis ZSet score by {@code clinic.starvation-score-reduction}
     * (lower score = higher priority in ascending-score ZSet). This gradually
     * floats long-waiting tokens toward the front of the queue.
     *
     * Only one cluster instance executes per cycle (leader lock, 4-min lease).
     */
    @Scheduled(fixedDelay = 300_000)   // every 5 minutes
    public void applyStarvationBoosts() {
        lockService.executeWithLockIfAvailable("lock:job:starvation-boost", 240, this::doApplyStarvationBoosts);
    }

    /**
     * Emits wait-time Timer samples for all currently WAITING tokens.
     * Runs every minute, locked to one instance.
     * Metric: smartqueue.queue.wait_time (histogram + summary) by doctorId.
     */
    @Scheduled(fixedDelay = 60_000)
    public void recordQueueWaitTimes() {
        lockService.executeWithLockIfAvailable("lock:job:wait-metrics", 55, this::doRecordWaitTimes);
    }

    // ── Job implementations (run only by the lock winner) ─────────────────────

    private void doExpireCalledTokens() {
        List<Token> expired = tokenRepository
                .findByStatusAndCalledAtBefore(
                        TokenStatus.CALLED,
                        LocalDateTime.now().minusMinutes(noShowTimeoutMins)
                );

        for (Token token : expired) {
            try {
                token.setStatus(TokenStatus.EXPIRED);
                tokenRepository.save(token);

                redisTemplate.opsForZSet().remove(
                        "queue:doctor:" + token.getDoctorId(),
                        token.getId().toString()
                );

                broadcastService.broadcastDoctorQueue(
                        token.getDoctorId(),
                        doctorQueueService.buildDoctorQueueDTO(token.getDoctorId())
                );

                autoExpiredCounter.increment();
                log.info("Auto-expired token {} (no-show timeout)", token.getTokenNumber());
            } catch (Exception e) {
                log.error("Failed to expire token {}: {}", token.getTokenNumber(), e.getMessage());
            }
        }
    }

    private void doApplyStarvationBoosts() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(starvationBoostAfterMins);
        List<Token> longWaiting = tokenRepository
                .findByStatusAndCreatedAtBefore(TokenStatus.WAITING, threshold);

        if (longWaiting.isEmpty()) {
            return;
        }

        log.info("Starvation boost: {} tokens have been waiting >{} min",
                longWaiting.size(), starvationBoostAfterMins);

        for (Token token : longWaiting) {
            try {
                String key = QUEUE_KEY + token.getDoctorId();
                Double currentScore = redisTemplate.opsForZSet()
                        .score(key, token.getId().toString());

                if (currentScore == null) continue;  // already removed (called/expired)

                // Boost: lower score = higher priority. Floor at 1 to avoid negatives.
                long boostedScore = Math.max(1L, currentScore.longValue() - starvationScoreReduction);
                redisTemplate.opsForZSet().add(key, token.getId().toString(), boostedScore);

                token.setDynamicScore(boostedScore);
                token.setLastScoreUpdate(LocalDateTime.now());
                tokenRepository.save(token);

                starvationBoostCounter.increment();
                log.debug("Starvation boost applied: token={} doctor={} {} -> {}",
                        token.getTokenNumber(), token.getDoctorId(), currentScore.longValue(), boostedScore);
            } catch (Exception e) {
                log.warn("Starvation boost failed for token {}: {}", token.getTokenNumber(), e.getMessage());
            }
        }

        // Rebroadcast affected doctors so UIs reflect the reordering
        longWaiting.stream()
                .map(Token::getDoctorId)
                .distinct()
                .forEach(doctorId -> {
                    try {
                        broadcastService.broadcastDoctorQueue(
                                doctorId,
                                doctorQueueService.buildDoctorQueueDTO(doctorId)
                        );
                    } catch (Exception e) {
                        log.warn("Broadcast after starvation boost failed for doctor {}: {}", doctorId, e.getMessage());
                    }
                });
    }

    private void doRecordWaitTimes() {
        List<Token> waiting = tokenRepository.findByStatus(TokenStatus.WAITING);
        for (Token token : waiting) {
            if (token.getCreatedAt() == null) continue;
            long waitSeconds = Duration.between(token.getCreatedAt(), LocalDateTime.now()).getSeconds();
            Timer.builder("smartqueue.queue.wait_time")
                    .description("Current wait time for WAITING tokens")
                    .tag("doctorId", String.valueOf(token.getDoctorId()))
                    .tag("serviceType", token.getServiceType() != null ? token.getServiceType().name() : "UNKNOWN")
                    .register(meterRegistry)
                    .record(Duration.ofSeconds(waitSeconds));
        }
    }
}

