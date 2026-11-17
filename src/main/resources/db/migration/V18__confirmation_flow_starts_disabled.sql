-- O fluxo automático de confirmação (pedido ao cliente e aviso ao admin) começa DESLIGADO.
-- Motivo: o app que está nas lojas só conhece SCHEDULED/COMPLETED/CANCELED e esconderia agendamentos CONFIRMED.
-- O admin liga depois (tela "Confirmação" do app novo ou PUT /api/v1/admin/settings/appointments), quando a versão
-- nova do app já estiver instalada. Se a linha já existir, não sobrescreve a escolha do admin.
INSERT INTO app_settings (config_key, config_value) VALUES ('appointment_confirmation_enabled', 'false')
ON DUPLICATE KEY UPDATE config_value = config_value;
