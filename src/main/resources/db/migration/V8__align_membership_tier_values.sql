UPDATE usuarios
SET tipo_membro = 'QUINZE_STANDARD'
WHERE tipo_membro = 'CLUB_15';

UPDATE agendamentos
SET tipo_atendimento = 'QUINZE_STANDARD'
WHERE tipo_atendimento = 'CLUB_15';
