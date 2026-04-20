package com.smartqueue.backend.service;

import com.smartqueue.backend.entity.Token;
import com.smartqueue.backend.enums.TokenStatus;
import com.smartqueue.backend.lock.RedissonLockService;
import com.smartqueue.backend.repository.TokenRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ClinicScheduledJobs}.
 *
 * Focus areas:
 *  - Starvation boost: correct score delta, floor clamping at 1, rebroadcast after boost
 *  - Auto-expire: CALLED tokens past timeout are set to EXPIRED and removed from ZSet
 *  - Distributed lock: job body only runs when lock is won (lock delegates tested via callback)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ClinicScheduledJobs")
class ClinicScheduledJobsTest {

    @Mock private TokenRepository tokenRepository;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private WebSocketBroadcastService broadcastService;
    @Mock private DoctorQueueService doctorQueueService;
    @Mock private RedissonLockService lockService;
    @Mock private ZSetOperations<String, String> zSetOps;

    private SimpleMeterRegistry meterRegistry;
    private ClinicScheduledJobs jobs;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        jobs = new ClinicScheduledJobs(
                tokenRepository, Optional.of(redisTemplate), broadcastService,
                doctorQueueService, lockService, meterRegistry
        );
        // Inject @Value fields
        ReflectionTestUtils.setField(jobs, "noShowTimeoutMins", 10);
        ReflectionTestUtils.setField(jobs, "starvationBoostAfterMins", 30);
        ReflectionTestUtils.setField(jobs, "starvationScoreReduction", 500_000L);

        // Init counters manually (simulates @PostConstruct)
        ReflectionTestUtils.invokeMethod(jobs, "initMetrics");

        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOps);

        // Lock service: immediately execute the runnable (simulate winning the lock)
        lenient().doAnswer(inv -> {
            ((Runnable) inv.getArgument(2)).run();
            return null;
        }).when(lockService).executeWithLockIfAvailable(anyString(), anyLong(), any(Runnable.class));
    }

    // ── Starvation boost ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("doApplyStarvationBoosts()")
    class StarvationBoost {

        private Token makeWaitingToken(Long id, Long doctorId, long currentRedisScore) {
            Token t = new Token();
            t.setId(id);
            t.setDoctorId(doctorId);
            t.setStatus(TokenStatus.WAITING);
            t.setCreatedAt(LocalDateTime.now().minusMinutes(45)); // older than threshold
            t.setTokenNumber("D" + doctorId + "-T" + id);
            when(zSetOps.score("queue:doctor:" + doctorId, id.toString()))
                    .thenReturn((double) currentRedisScore);
            return t;
        }

        @Test
        @DisplayName("reduces ZSet score by starvationScoreReduction for long-waiting token")
        void reducesScoreByConfiguredAmount() {
            Token t = makeWaitingToken(1L, 10L, 2_000_000L);
            when(tokenRepository.findByStatusAndCreatedAtBefore(eq(TokenStatus.WAITING), any()))
                    .thenReturn(List.of(t));
            when(doctorQueueService.buildDoctorQueueDTO(anyLong())).thenReturn(null);

            jobs.applyStarvationBoosts();

            ArgumentCaptor<Double> scoreCaptor = ArgumentCaptor.forClass(Double.class);
            verify(zSetOps).add(eq("queue:doctor:10"), eq("1"), scoreCaptor.capture());
            assertThat(scoreCaptor.getValue()).isEqualTo(1_500_000.0);  // 2_000_000 - 500_000
        }

        @Test
        @DisplayName("score is floored at 1 (never goes negative or zero)")
        void floorAtOne() {
            Token t = makeWaitingToken(2L, 10L, 300_000L); // score < reduction
            when(tokenRepository.findByStatusAndCreatedAtBefore(eq(TokenStatus.WAITING), any()))
                    .thenReturn(List.of(t));
            when(doctorQueueService.buildDoctorQueueDTO(anyLong())).thenReturn(null);

            jobs.applyStarvationBoosts();

            ArgumentCaptor<Double> scoreCaptor = ArgumentCaptor.forClass(Double.class);
            verify(zSetOps).add(eq("queue:doctor:10"), eq("2"), scoreCaptor.capture());
            assertThat(scoreCaptor.getValue()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("token already removed from Redis (null score) is silently skipped")
        void skipsTokenRemovedFromRedis() {
            Token t = new Token();
            t.setId(3L);
            t.setDoctorId(10L);
            t.setStatus(TokenStatus.WAITING);
            t.setCreatedAt(LocalDateTime.now().minusMinutes(60));
            lenient().when(zSetOps.score("queue:doctor:10", "3")).thenReturn(null);

            lenient().when(tokenRepository.findByStatusAndCreatedAtBefore(eq(TokenStatus.WAITING), any()))
                    .thenReturn(List.of(t));

            jobs.applyStarvationBoosts();

            // No update should be written for this token
            verify(zSetOps, never()).add(anyString(), anyString(), anyDouble());
        }

        @Test
        @DisplayName("starvation_boost counter is incremented once per boosted token")
        void counterIncrementedPerToken() {
            Token t1 = makeWaitingToken(4L, 10L, 1_000_000L);
            Token t2 = makeWaitingToken(5L, 11L, 800_000L);
            when(tokenRepository.findByStatusAndCreatedAtBefore(eq(TokenStatus.WAITING), any()))
                    .thenReturn(List.of(t1, t2));
            when(doctorQueueService.buildDoctorQueueDTO(anyLong())).thenReturn(null);

            jobs.applyStarvationBoosts();

            double count = meterRegistry.counter("smartqueue.token.starvation_boost").count();
            assertThat(count).isEqualTo(2.0);
        }

        @Test
        @DisplayName("rebroadcast is called once per affected doctor (deduplicated)")
        void rebroadcastDeduplicated() {
            // Two tokens for the SAME doctor — should only rebroadcast once
            Token t1 = makeWaitingToken(6L, 20L, 1_000_000L);
            Token t2 = makeWaitingToken(7L, 20L, 900_000L);
            when(tokenRepository.findByStatusAndCreatedAtBefore(eq(TokenStatus.WAITING), any()))
                    .thenReturn(List.of(t1, t2));
            when(doctorQueueService.buildDoctorQueueDTO(20L)).thenReturn(null);

            jobs.applyStarvationBoosts();

            verify(broadcastService, times(1)).broadcastDoctorQueue(eq(20L), any());
        }

        @Test
        @DisplayName("no tokens waiting → nothing written to Redis, no broadcast")
        void noTokens_noWork() {
            lenient().when(tokenRepository.findByStatusAndCreatedAtBefore(eq(TokenStatus.WAITING), any()))
                    .thenReturn(List.of());

            jobs.applyStarvationBoosts();

            verify(zSetOps, never()).add(anyString(), anyString(), anyDouble());
            verify(broadcastService, never()).broadcastDoctorQueue(anyLong(), any());
        }
    }

    // ── Auto-expire ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("doExpireCalledTokens()")
    class AutoExpire {

        @Test
        @DisplayName("CALLED token past timeout → status set to EXPIRED and removed from ZSet")
        void expiresStalledCalledToken() {
            Token t = new Token();
            t.setId(10L);
            t.setDoctorId(5L);
            t.setStatus(TokenStatus.CALLED);
            t.setTokenNumber("D5-T10");

            when(tokenRepository.findByStatusAndCalledAtBefore(eq(TokenStatus.CALLED), any()))
                    .thenReturn(List.of(t));
            when(doctorQueueService.buildDoctorQueueDTO(5L)).thenReturn(null);

            jobs.autoExpireCalledTokens();

            assertThat(t.getStatus()).isEqualTo(TokenStatus.EXPIRED);
            verify(tokenRepository).save(t);
            verify(zSetOps).remove("queue:doctor:5", "10");
            verify(broadcastService).broadcastDoctorQueue(eq(5L), any());
        }

        @Test
        @DisplayName("auto_expired counter incremented for each expired token")
        void counterIncrementedPerExpiredToken() {
            Token t1 = new Token(); t1.setId(11L); t1.setDoctorId(5L); t1.setTokenNumber("D5-T11");
            Token t2 = new Token(); t2.setId(12L); t2.setDoctorId(5L); t2.setTokenNumber("D5-T12");

            when(tokenRepository.findByStatusAndCalledAtBefore(eq(TokenStatus.CALLED), any()))
                    .thenReturn(List.of(t1, t2));
            when(doctorQueueService.buildDoctorQueueDTO(5L)).thenReturn(null);

            jobs.autoExpireCalledTokens();

            double count = meterRegistry.counter("smartqueue.token.auto_expired").count();
            assertThat(count).isEqualTo(2.0);
        }

        @Test
        @DisplayName("exception during single token expiry is caught; other tokens still processed")
        void exceptionInOneTokenDoesNotStopOthers() {
            Token good = new Token();
            good.setId(13L); good.setDoctorId(5L); good.setTokenNumber("D5-T13");

            Token bad = new Token();
            bad.setId(14L); bad.setDoctorId(5L); bad.setTokenNumber("D5-T14");

            when(tokenRepository.findByStatusAndCalledAtBefore(eq(TokenStatus.CALLED), any()))
                    .thenReturn(List.of(bad, good));
            when(doctorQueueService.buildDoctorQueueDTO(anyLong())).thenReturn(null);

            // Make the first token throw on save
            doThrow(new RuntimeException("DB error")).when(tokenRepository).save(bad);

            // Should not throw — good token is still processed
            jobs.autoExpireCalledTokens();

            verify(tokenRepository, times(2)).save(any()); // tried both
            assertThat(good.getStatus()).isEqualTo(TokenStatus.EXPIRED);
        }
    }
}
