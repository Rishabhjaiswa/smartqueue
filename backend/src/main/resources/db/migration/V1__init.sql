CREATE TABLE tokens (
                        id              BIGSERIAL PRIMARY KEY,
                        token_number    VARCHAR(10)  NOT NULL,
                        service_type    VARCHAR(50)  NOT NULL,
                        status          VARCHAR(20)  NOT NULL DEFAULT 'WAITING',
                        priority_score  BIGINT       NOT NULL,
                        office_id       INTEGER      NOT NULL DEFAULT 1,
                        created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
                        called_at       TIMESTAMP,
                        completed_at    TIMESTAMP
);

CREATE INDEX idx_tokens_office_status
    ON tokens(office_id, status);

CREATE TABLE staff_users (
                             id        BIGSERIAL PRIMARY KEY,
                             username  VARCHAR(50)  UNIQUE NOT NULL,
                             password  VARCHAR(255) NOT NULL,
                             office_id INTEGER      NOT NULL DEFAULT 1,
                             role      VARCHAR(20)  NOT NULL DEFAULT 'STAFF'
);