-- Fluxo de confirmação e proposta de remarcação de agendamentos.
--   confirmed_at: quando o agendamento foi confirmado (pelo cliente ou mantido pelo admin).
--   proposed_at : novo horário proposto pelo cliente, aguardando aprovação do admin.
-- Os novos status (CONFIRMED, RESCHEDULE_REQUESTED) são gravados como texto na coluna status (VARCHAR).
ALTER TABLE agendamentos
    ADD COLUMN confirmed_at TIMESTAMP NULL DEFAULT NULL,
    ADD COLUMN proposed_at  TIMESTAMP NULL DEFAULT NULL;
