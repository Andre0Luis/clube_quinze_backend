-- A notificação interna passa a guardar a que agendamento ela se refere e o tipo de ação, para o app abrir
-- a tela certa quando o usuário (ou o admin) toca nela na lista "Minhas Notificações".
ALTER TABLE notificacoes ADD COLUMN agendamento_id BIGINT NULL;
ALTER TABLE notificacoes ADD COLUMN acao VARCHAR(64) NULL;
