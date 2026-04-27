-- Phase 2: Allow shared mobile numbers (family members)
-- Remove the UNIQUE constraint from patients.phone so multiple family members
-- can share one phone number. Name verification is used to distinguish them.

ALTER TABLE patients DROP CONSTRAINT IF EXISTS patients_phone_key;

-- Composite index for fast phone+name lookups (replaces the unique index)
CREATE INDEX IF NOT EXISTS idx_patients_phone ON patients(phone);
CREATE INDEX IF NOT EXISTS idx_patients_phone_name ON patients(phone, lower(name));
