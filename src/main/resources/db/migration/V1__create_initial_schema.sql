CREATE TABLE planos (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(255) NOT NULL UNIQUE,
    descricao VARCHAR(2048),
    valor DECIMAL(10, 2) NOT NULL,
    duracao_meses INT NOT NULL
);

CREATE TABLE admins (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    senha_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    ultimo_login TIMESTAMP NULL,
    ativo BOOLEAN NOT NULL
);

CREATE TABLE usuarios (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    senha_hash VARCHAR(255) NOT NULL,
    telefone VARCHAR(255),
    data_nascimento DATE,
    tipo_membro VARCHAR(50),
    role VARCHAR(50) NOT NULL,
    plano_id BIGINT,
    data_cadastro TIMESTAMP NOT NULL,
    ultimo_login TIMESTAMP NULL,
    ativo BOOLEAN NOT NULL,
    CONSTRAINT fk_usuarios_plano FOREIGN KEY (plano_id) REFERENCES planos (id)
);

CREATE TABLE preferencias_usuarios (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    tipo_preferencia VARCHAR(255) NOT NULL,
    detalhe_preferencia VARCHAR(255) NOT NULL,
    criado_em TIMESTAMP NOT NULL,
    atualizado_em TIMESTAMP NOT NULL,
    CONSTRAINT fk_preferencias_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE CASCADE
);

CREATE TABLE refresh_tokens (
    id CHAR(36) NOT NULL PRIMARY KEY,
    token VARCHAR(255) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL,
    CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES usuarios (id) ON DELETE CASCADE
);

CREATE TABLE agendamentos (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    data_horario TIMESTAMP NOT NULL,
    tipo_atendimento VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    servico VARCHAR(255),
    criado_em TIMESTAMP NOT NULL,
    atualizado_em TIMESTAMP NOT NULL,
    notes VARCHAR(2048),
    CONSTRAINT fk_agendamentos_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE CASCADE
);

CREATE TABLE notificacoes (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    usuario_id BIGINT,
    titulo VARCHAR(255) NOT NULL,
    mensagem VARCHAR(2048) NOT NULL,
    tipo VARCHAR(50) NOT NULL,
    lida BOOLEAN NOT NULL,
    enviada_em TIMESTAMP NULL,
    CONSTRAINT fk_notificacoes_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE SET NULL
);

CREATE TABLE pagamentos (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    valor DECIMAL(10, 2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    metodo_pagamento VARCHAR(255),
    data_pagamento TIMESTAMP NULL,
    descricao VARCHAR(255),
    CONSTRAINT fk_pagamentos_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE CASCADE
);

CREATE TABLE feedbacks (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    agendamento_id BIGINT NOT NULL,
    usuario_id BIGINT NOT NULL,
    nota INT NOT NULL,
    comentario VARCHAR(2048),
    criado_em TIMESTAMP NOT NULL,
    CONSTRAINT fk_feedbacks_agendamento FOREIGN KEY (agendamento_id) REFERENCES agendamentos (id) ON DELETE CASCADE,
    CONSTRAINT fk_feedbacks_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE CASCADE
);

CREATE TABLE community_posts (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    author_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    content VARCHAR(4096) NOT NULL,
    image_url VARCHAR(1024),
    image_base64 LONGTEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_community_posts_author FOREIGN KEY (author_id) REFERENCES usuarios (id) ON DELETE CASCADE
);

CREATE TABLE community_comments (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    content VARCHAR(2048) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_community_comments_post FOREIGN KEY (post_id) REFERENCES community_posts (id) ON DELETE CASCADE,
    CONSTRAINT fk_community_comments_author FOREIGN KEY (author_id) REFERENCES usuarios (id) ON DELETE CASCADE
);

CREATE TABLE community_likes (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_community_likes_post FOREIGN KEY (post_id) REFERENCES community_posts (id) ON DELETE CASCADE,
    CONSTRAINT fk_community_likes_user FOREIGN KEY (user_id) REFERENCES usuarios (id) ON DELETE CASCADE,
    CONSTRAINT uk_community_like UNIQUE (post_id, user_id)
);

CREATE TABLE recomendacoes (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    titulo VARCHAR(255) NOT NULL,
    descricao VARCHAR(2048),
    url VARCHAR(1024),
    status VARCHAR(50) NOT NULL,
    enviada_por BIGINT,
    aprovada_por BIGINT,
    enviada_em TIMESTAMP NOT NULL,
    atualizada_em TIMESTAMP NULL,
    CONSTRAINT fk_recomendacoes_enviada_por FOREIGN KEY (enviada_por) REFERENCES usuarios (id) ON DELETE SET NULL,
    CONSTRAINT fk_recomendacoes_aprovada_por FOREIGN KEY (aprovada_por) REFERENCES admins (id) ON DELETE SET NULL
);
