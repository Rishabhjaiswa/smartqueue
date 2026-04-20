-- V6: Performance indexes for distributed system readiness
-- Uses CONCURRENTLY so existing live traffic is not blocked during index creation.
-- NOTE: CONCURRENTLY cannot run inside a transaction block.
-- Flyway runs each migration in a transaction by default, so we disable it here.

-- Most critical: duplicate-token check in QueueService.generateToken()
-- Query: findFirstByPatientIdAndStatusIn(patientId, [WAITING, CALLED, IN_CONSULTATION])
CREATE INDEX IF NOT EXISTS idx_tokens_patient_active_status
    ON tokens (patient_id, status)
    WHERE status IN ('WAITING', 'CALLED', 'IN_CONSULTATION');

-- Priority recalc job: iterates all WAITING tokens per doctor
-- Query: find tokens by doctorId + status = WAITING
CREATE INDEX IF NOT EXISTS idx_tokens_doctor_waiting
    ON tokens (doctor_id, status)
    WHERE status = 'WAITING';

-- No-show expiry job: findByStatusAndCalledAtBefore(CALLED, cutoff)
CREATE INDEX IF NOT EXISTS idx_tokens_called_expiry
    ON tokens (status, called_at)
    WHERE status = 'CALLED';

-- History / analytics range scans by date
CREATE INDEX IF NOT EXISTS idx_tokens_created_at
    ON tokens USING BRIN (created_at);

-- Recent history endpoint: findTop20ByStatusOrderByConsultationEndDesc
CREATE INDEX IF NOT EXISTS idx_tokens_completed_history
    ON tokens (status, consultation_end DESC)
    WHERE status = 'COMPLETED';

-- Audit log time-range queries (already has created_at index but make it partial)
CREATE INDEX IF NOT EXISTS idx_audit_actor_created
    ON audit_logs (actor_username, created_at DESC);

-- Patient lookup by phone (used in chat + Telegram flows)
-- Partial: only index non-null phones (avoids indexing anonymous walk-ins)
CREATE INDEX IF NOT EXISTS idx_patients_phone
    ON patients (phone)
    WHERE phone IS NOT NULL;
