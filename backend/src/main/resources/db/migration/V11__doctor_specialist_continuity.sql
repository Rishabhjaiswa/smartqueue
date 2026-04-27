-- Phase 3: Doctor-Specialist Continuity of Care
-- 1. Add preferred_doctor_id to patients (nullable FK → doctors)
-- 2. Create doctor_assignment_history for per-patient visit audit trail

-- ── preferred_doctor_id on patients ──────────────────────────────────────
ALTER TABLE patients
    ADD COLUMN IF NOT EXISTS preferred_doctor_id BIGINT
        REFERENCES doctors(id) ON DELETE SET NULL;

-- ── doctor_assignment_history ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS doctor_assignment_history (
    id                  BIGSERIAL PRIMARY KEY,
    patient_id          BIGINT        NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
    doctor_id           BIGINT        NOT NULL REFERENCES doctors(id)  ON DELETE RESTRICT,
    token_id            BIGINT                 REFERENCES tokens(id)   ON DELETE SET NULL,
    assigned_at         TIMESTAMP     NOT NULL DEFAULT NOW(),
    specialization      VARCHAR(64),
    visit_type          VARCHAR(32),
    chief_complaint     TEXT,
    notes               TEXT
);

CREATE INDEX IF NOT EXISTS idx_dah_patient   ON doctor_assignment_history(patient_id);
CREATE INDEX IF NOT EXISTS idx_dah_doctor    ON doctor_assignment_history(doctor_id);
CREATE INDEX IF NOT EXISTS idx_dah_assigned  ON doctor_assignment_history(assigned_at DESC);
