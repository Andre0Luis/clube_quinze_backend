-- Integração com o Google Agenda: conexão da conta do administrador e vínculo de cada agendamento com o evento.
--   google_calendar_connection: no máximo uma linha (a conta do clube); o refresh token fica CRIPTOGRAFADO (AES-GCM).
--   agendamentos.google_event_id: id do evento criado na agenda do Google.
--   agendamentos.google_synced_for: valor de atualizado_em já refletido no Google (evita sincronizar em loop).
CREATE TABLE IF NOT EXISTS google_calendar_connection (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    connected_by_user_id BIGINT NOT NULL,
    google_email VARCHAR(255) NULL,
    refresh_token_enc VARCHAR(2000) NOT NULL,
    scopes VARCHAR(1000) NULL,
    calendar_id VARCHAR(255) NULL,
    calendar_summary VARCHAR(255) NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE agendamentos
    ADD COLUMN google_event_id VARCHAR(255) NULL DEFAULT NULL,
    ADD COLUMN google_synced_for TIMESTAMP NULL DEFAULT NULL;
