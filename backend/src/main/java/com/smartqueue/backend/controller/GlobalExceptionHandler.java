package com.smartqueue.backend.controller;

import com.smartqueue.backend.lock.LockAcquisitionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;

/**
 * Centralised exception-to-HTTP mapping.
 *
 * Uses RFC 9457 ProblemDetail (Spring 6 / Boot 3 built-in) so all error
 * responses have a consistent JSON shape:
 * {
 *   "type":   "https://smartqueue.io/errors/lock-timeout",
 *   "title":  "Service Temporarily Unavailable",
 *   "status": 503,
 *   "detail": "Timed out waiting for lock: lock:token:patient:42",
 *   "timestamp": "2024-01-01T12:00:00Z"
 * }
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * LockAcquisitionException → 503 Service Unavailable.
     *
     * Retrying after a short backoff is safe — the lock is held for a bounded
     * duration (5 s for token generation, 55 s for scheduled jobs).
     */
    @ExceptionHandler(LockAcquisitionException.class)
    public ProblemDetail handleLockTimeout(LockAcquisitionException ex) {
        log.warn("Lock acquisition timeout: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                ex.getMessage()
        );
        problem.setType(URI.create("https://smartqueue.io/errors/lock-timeout"));
        problem.setTitle("Service Temporarily Unavailable");
        problem.setProperty("timestamp", Instant.now().toString());
        problem.setProperty("retryAfterMs", 200);
        return problem;
    }

    /**
     * IllegalArgumentException → 400 Bad Request.
     *
     * Catches things like "You recently booked a token" from QueueService
     * which currently throw IllegalArgumentException.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadRequest(IllegalArgumentException ex) {
        log.debug("Bad request: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ex.getMessage()
        );
        problem.setType(URI.create("https://smartqueue.io/errors/bad-request"));
        problem.setTitle("Bad Request");
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }
}
