package com.smartqueue.backend.lock;

/**
 * Thrown when a distributed lock cannot be acquired within the configured timeout.
 * Callers should treat this as a 503 / retry-able condition, not a 500.
 */
public class LockAcquisitionException extends RuntimeException {
    public LockAcquisitionException(String message) {
        super(message);
    }
}
