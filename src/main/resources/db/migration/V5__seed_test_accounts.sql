INSERT INTO admins (nome, email, senha_hash, role, ativo)
VALUES
    ('Administrador Clube 15', 'admin@clubequinze.com', '$2b$12$rfD1sCDV3YPGzTq05xhTruqTid3PW3WIymT7fDTWPJqfnCrpr5IPG', 'MASTER', TRUE);

INSERT INTO usuarios (nome, email, senha_hash, tipo_membro, role, data_cadastro, ativo)
VALUES
    ('Cliente Clube 15', 'cliente@clubequinze.com', '$2b$12$rfD1sCDV3YPGzTq05xhTruqTid3PW3WIymT7fDTWPJqfnCrpr5IPG', 'QUINZE_STANDARD', 'CLUB_STANDARD', CURRENT_TIMESTAMP, TRUE),
    ('Cliente Select', 'select@clubequinze.com', '$2b$12$rfD1sCDV3YPGzTq05xhTruqTid3PW3WIymT7fDTWPJqfnCrpr5IPG', 'QUINZE_SELECT', 'CLUB_SELECT', CURRENT_TIMESTAMP, TRUE);
