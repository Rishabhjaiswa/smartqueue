CREATE TABLE patients (
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(100) NOT NULL,
    phone            VARCHAR(15)  UNIQUE NOT NULL,
    age              INTEGER      NOT NULL,
    gender           VARCHAR(10),
    blood_group      VARCHAR(5),
    abha_id          VARCHAR(20),
    telegram_chat_id BIGINT,
    created_at       TIMESTAMP DEFAULT NOW()
);

CREATE TABLE doctors (
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(100) NOT NULL,
    specialization   VARCHAR(100) NOT NULL,
    room_number      VARCHAR(10),
    is_available     BOOLEAN  DEFAULT TRUE,
    avg_consult_mins INTEGER  DEFAULT 10,
    max_queue_size   INTEGER  DEFAULT 25
);

CREATE TABLE appointments (
    id               BIGSERIAL PRIMARY KEY,
    patient_id       BIGINT REFERENCES patients(id),
    doctor_id        BIGINT REFERENCES doctors(id),
    scheduled_time   TIMESTAMP NOT NULL,
    status           VARCHAR(20) DEFAULT 'SCHEDULED',
    notes            TEXT
);

CREATE INDEX idx_appointments_doctor_time
    ON appointments(doctor_id, scheduled_time);