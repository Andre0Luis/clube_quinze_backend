-- Tabela genérica de configurações da aplicação (key/value).
-- Usada inicialmente para os lembretes do admin (habilitado + offsets em minutos).
CREATE TABLE IF NOT EXISTS app_settings (
    config_key   VARCHAR(100) PRIMARY KEY,
    config_value VARCHAR(255) NOT NULL,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT INTO app_settings (config_key, config_value) VALUES
    ('admin_reminder_enabled', 'true'),
    ('admin_reminder_offsets', '60,30')
ON DUPLICATE KEY UPDATE config_value = config_value;
