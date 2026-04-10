CREATE TABLE patients (
                          id               BIGSERIAL PRIMARY KEY,
                          name             VARCHAR(100) NOT NULL,
                          phone            VARCHAR(15)  UNIQUE NOT NULL,
                          age              INTEGER      NOT NULL,
                          blood_group      VARCHAR(5),
                          telegram_chat_id BIGINT,
                          registered_at    TIMESTAMP DEFAULT NOW()
);

CREATE TABLE doctors (
                         id                     BIGSERIAL PRIMARY KEY,
                         name                   VARCHAR(100) NOT NULL,
                         specialization         VARCHAR(100) NOT NULL,
                         is_available           BOOLEAN DEFAULT TRUE,
                         avg_consultation_minutes INTEGER DEFAULT 10,
                         session_start_time     TIME,
                         delay_minutes          INTEGER DEFAULT 0
);

CREATE TABLE appointments (
                              id             BIGSERIAL PRIMARY KEY,
                              patient_id     BIGINT REFERENCES patients(id),
                              doctor_id      BIGINT REFERENCES doctors(id),
                              scheduled_time TIMESTAMP NOT NULL,
                              visit_type     VARCHAR(20) DEFAULT 'WALK_IN',
                              status         VARCHAR(20) DEFAULT 'SCHEDULED'
);

ALTER TABLE tokens
    ADD COLUMN patient_id              BIGINT REFERENCES patients(id),
    ADD COLUMN doctor_id               BIGINT REFERENCES doctors(id),
    ADD COLUMN visit_type              VARCHAR(20) DEFAULT 'WALK_IN',
    ADD COLUMN severity_score          INTEGER DEFAULT 0,
    ADD COLUMN dynamic_score           BIGINT,
    ADD COLUMN actual_consultation_minutes INTEGER,
    ADD COLUMN last_score_update       TIMESTAMP DEFAULT NOW(),
    ADD COLUMN appointment_id          BIGINT REFERENCES appointments(id);

ALTER TABLE staff_users
    ADD COLUMN IF NOT EXISTS role VARCHAR(20) NOT NULL DEFAULT 'RECEPTIONIST',
    ADD COLUMN IF NOT EXISTS doctor_id BIGINT REFERENCES doctors(id);

CREATE TABLE audit_events (
                              id          BIGSERIAL PRIMARY KEY,
                              action      VARCHAR(60) NOT NULL,
                              entity_type VARCHAR(30),
                              entity_id   BIGINT,
                              actor       VARCHAR(60),
                              actor_role  VARCHAR(20),
                              note        TEXT,
                              created_at  TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_tokens_doctor ON tokens(doctor_id, status);
CREATE INDEX idx_audit_created ON audit_events(created_at DESC);