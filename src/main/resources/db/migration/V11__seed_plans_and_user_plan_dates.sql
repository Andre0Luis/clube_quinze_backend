ALTER TABLE usuarios
    ADD COLUMN data_renovacao_plano DATE,
    ADD COLUMN data_encerramento_plano DATE;

INSERT INTO planos (nome, descricao, valor, duracao_meses)
SELECT 'QUINZE_STANDARD', 'Plano QUINZE_STANDARD', 0.00, 1
WHERE NOT EXISTS (SELECT 1 FROM planos WHERE nome = 'QUINZE_STANDARD');

INSERT INTO planos (nome, descricao, valor, duracao_meses)
SELECT 'QUINZE_PREMIUM', 'Plano QUINZE_PREMIUM', 0.00, 1
WHERE NOT EXISTS (SELECT 1 FROM planos WHERE nome = 'QUINZE_PREMIUM');

INSERT INTO planos (nome, descricao, valor, duracao_meses)
SELECT 'QUINZE_SELECT', 'Plano QUINZE_SELECT', 0.00, 1
WHERE NOT EXISTS (SELECT 1 FROM planos WHERE nome = 'QUINZE_SELECT');

UPDATE usuarios u
JOIN planos p ON p.nome = u.tipo_membro
SET u.plano_id = p.id;

UPDATE usuarios
SET data_renovacao_plano = IFNULL(data_renovacao_plano, DATE_ADD(CURDATE(), INTERVAL 1 MONTH)),
    data_encerramento_plano = IFNULL(data_encerramento_plano, DATE_ADD(CURDATE(), INTERVAL 1 MONTH));