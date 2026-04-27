-- requiresAssistance flag: patients needing mobility/language/cognitive help
-- are automatically boosted to near-front of queue by PriorityEngine.
ALTER TABLE tokens
    ADD COLUMN requires_assistance BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_tokens_requires_assistance ON tokens(requires_assistance) WHERE requires_assistance = TRUE;
