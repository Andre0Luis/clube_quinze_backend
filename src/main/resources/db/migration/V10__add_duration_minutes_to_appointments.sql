-- Add duration to appointments to match entity field
ALTER TABLE agendamentos
    ADD COLUMN IF NOT EXISTS duration_minutes INT NOT NULL DEFAULT 60;

-- Ensure existing rows have a value
UPDATE agendamentos
SET duration_minutes = 60
WHERE duration_minutes IS NULL;