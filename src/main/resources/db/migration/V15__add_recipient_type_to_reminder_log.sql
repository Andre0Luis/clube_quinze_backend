-- Distingue lembretes do CLIENTE dos lembretes do ADMIN no log de idempotência,
-- permitindo que ambos sejam enviados para o mesmo agendamento/offset sem colidir.
ALTER TABLE lembrete_agendamento_log
    ADD COLUMN recipient_type VARCHAR(20) NOT NULL DEFAULT 'CLIENT';

-- Substitui a unique antiga (appointment_id, offset_minutes) por uma que inclui o destinatário.
ALTER TABLE lembrete_agendamento_log
    DROP INDEX uq_lembrete_appt_offset;

ALTER TABLE lembrete_agendamento_log
    ADD CONSTRAINT uq_lembrete_appt_offset_recipient
    UNIQUE (appointment_id, offset_minutes, recipient_type);
