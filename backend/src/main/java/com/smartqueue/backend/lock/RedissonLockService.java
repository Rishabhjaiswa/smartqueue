package com.smartqueue.backend.lock;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Distributed lock service backed by Redisson.
 *
 * All critical queue-mutation paths must acquire a lock via this service
 * before touching Redis ZSets or the tokens table. This prevents race
 * conditions that existed in the original single-instance implementation
 * when the app scales horizontally.
 *
 * Lock keys used in SmartQueue:
 *   queue-insert:doctor:{doctorId}   — token generation + ZSet add
 *   rebalance:office:{officeId}      — redistributeQueue / rebalanceAvailableQueues
 *   score-recalc:doctor:{doctorId}   — priority recalculation per doctor
 *   noshow-expire:token:{tokenId}    — no-show expiry per token
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedissonLockService {

    private final Optional<RedissonClient> redissonClient;
    private final MeterRegistry meterRegistry;

    @Value("${app.redis.required:false}")
    private boolean redisRequired;

    public <T> T executeWithLock(String lockKey, long waitMs, Supplier<T> action) {
        if (redissonClient.isEmpty()) {
            if (redisRequired) {
                throw new IllegalStateException("Redis required but not available. Cannot execute lock for: " + lockKey);
            }
            log.warn("Redis disabled: Bypassing distributed lock for {}", lockKey);
            return action.get();
        }
        RLock lock = redissonClient.get().getLock(lockKey);
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            boolean acquired = lock.tryLock(waitMs, waitMs * 2L, TimeUnit.MILLISECONDS);
            if (!acquired) {
                meterRegistry.counter("smartqueue.redis.lock.timeout",
                        "lockKey", normaliseLockKey(lockKey)).increment();
                log.warn("Could not acquire distributed lock within {}ms: {}", waitMs, lockKey);
                throw new LockAcquisitionException("Timed out waiting for lock: " + lockKey);
            }
            log.debug("Acquired lock: {}", lockKey);
            return action.get();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockAcquisitionException("Lock acquisition interrupted: " + lockKey);
        } finally {
            sample.stop(meterRegistry.timer("smartqueue.redis.lock.wait",
                    "lockKey", normaliseLockKey(lockKey)));
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("Released lock: {}", lockKey);
            }
        }
    }

    /**
     * Void variant — convenience wrapper for actions that return nothing.
     */
    public void executeWithLock(String lockKey, long waitMs, Runnable action) {
        executeWithLock(lockKey, waitMs, () -> {
            action.run();
            return null;
        });
    }

    /**
     * Try-and-skip variant for scheduled jobs.
     *
     * Attempts to acquire the lock with 0 wait time (no blocking).
     * If another instance already holds the lock, this method returns immediately
     * without executing {@code action} and without throwing — the job is simply
     * skipped on this instance for this cycle.
     *
     * @param lockKey      Redis key for the lock
     * @param leaseSecs    Lock lease time in seconds (auto-expires if holder crashes)
     * @param action       Job logic to run only by the lock winner
     */
    public void executeWithLockIfAvailable(String lockKey, long leaseSecs, Runnable action) {
        if (redissonClient.isEmpty()) {
            if (redisRequired) {
                throw new IllegalStateException("Redis required but not available. Cannot execute lock for: " + lockKey);
            }
            log.warn("Redis disabled: Bypassing scheduled job lock for {}", lockKey);
            action.run();
            return;
        }
        RLock lock = redissonClient.get().getLock(lockKey);
        try {
            boolean acquired = lock.tryLock(0, leaseSecs, TimeUnit.SECONDS);
            if (!acquired) {
                log.debug("Skipping scheduled job — another instance holds lock: {}", lockKey);
                meterRegistry.counter("smartqueue.redis.lock.skipped",
                        "lockKey", normaliseLockKey(lockKey)).increment();
                return;
            }
            log.debug("Leader lock acquired for scheduled job: {}", lockKey);
            try {
                action.run();
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while acquiring leader lock: {}", lockKey);
        }
    }


    /**
     * Strips dynamic IDs from lock keys so Prometheus doesn't create unbounded label cardinality.
     * e.g. "queue-insert:doctor:42" → "queue-insert:doctor"
     */
    private String normaliseLockKey(String lockKey) {
        // Remove trailing ":number" segment
        return lockKey.replaceAll(":\\d+$", "");
    }
}
