-- Adiciona suporte a agendamentos recorrentes na tabela de agendamentos
ALTER TABLE agendamentos
    ADD COLUMN IF NOT EXISTS recurrence_group_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS recurrence_period   VARCHAR(20);

-- Índice para consultas de grupo de recorrência
CREATE INDEX IF NOT EXISTS idx_agendamentos_recurrence_group
    ON agendamentos (recurrence_group_id);
