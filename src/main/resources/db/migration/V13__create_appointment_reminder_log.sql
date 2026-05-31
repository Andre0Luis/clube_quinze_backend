-- Log de idempotência dos lembretes de agendamento.
-- Garante que cada (agendamento, offset) seja notificado no máximo uma vez,
-- permitindo janela de varredura com catch-up sem risco de duplicar.
CREATE TABLE IF NOT EXISTS lembrete_agendamento_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    appointment_id BIGINT NOT NULL,
    offset_minutes INT NOT NULL,
    sent_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_lembrete_appt_offset UNIQUE (appointment_id, offset_minutes),
    CONSTRAINT fk_lembrete_appt FOREIGN KEY (appointment_id) REFERENCES agendamentos(id) ON DELETE CASCADE
);

CREATE INDEX idx_lembrete_appt ON lembrete_agendamento_log(appointment_id);
