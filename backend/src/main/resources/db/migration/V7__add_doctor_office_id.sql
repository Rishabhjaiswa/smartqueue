-- Add office_id to doctors table for multi-tenant partitioning.
-- Default 1 ensures existing rows remain valid.
ALTER TABLE doctors
    ADD COLUMN office_id INTEGER NOT NULL DEFAULT 1;

CREATE INDEX idx_doctors_office_id ON doctors(office_id);
