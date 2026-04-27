-- Phase 0: DDL additions for PMS upgrade
-- Actual doctor + staff seeding is handled by SystemBootstrapInitializer (BCrypt via Spring)

-- Ensure staff_users.doctor_id column exists (idempotent guard)
DO $$ BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='staff_users' AND column_name='doctor_id'
    ) THEN
        ALTER TABLE staff_users ADD COLUMN doctor_id BIGINT REFERENCES doctors(id);
    END IF;
END $$;
