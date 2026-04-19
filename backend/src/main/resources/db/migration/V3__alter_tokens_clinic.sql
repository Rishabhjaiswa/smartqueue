ALTER TABLE tokens
    ADD COLUMN patient_id          BIGINT REFERENCES patients(id),
    ADD COLUMN doctor_id           BIGINT REFERENCES doctors(id),
    ADD COLUMN appointment_id      BIGINT REFERENCES appointments(id),
    ADD COLUMN visit_type          VARCHAR(20) NOT NULL DEFAULT 'WALK_IN',
    ADD COLUMN chief_complaint     TEXT,
    ADD COLUMN severity_score      INTEGER DEFAULT 0,
    ADD COLUMN dynamic_score       BIGINT,
    ADD COLUMN last_score_update   TIMESTAMP DEFAULT NOW(),
    ADD COLUMN consultation_start  TIMESTAMP,
    ADD COLUMN consultation_end    TIMESTAMP,
    ADD COLUMN consult_duration_mins INTEGER;

ALTER TABLE staff_users
    ADD COLUMN doctor_id  BIGINT REFERENCES doctors(id);

CREATE INDEX idx_tokens_doctor_status
    ON tokens(doctor_id, status);
CREATE INDEX idx_tokens_patient
    ON tokens(patient_id);