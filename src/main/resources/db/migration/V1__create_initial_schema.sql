SET search_path TO clube_quinze_app;

CREATE TABLE planos (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(255) NOT NULL UNIQUE,
    descricao VARCHAR(2048),
    valor NUMERIC(10, 2) NOT NULL,
    duracao_meses INTEGER NOT NULL
);

CREATE TABLE admins (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    senha_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    ultimo_login TIMESTAMP WITHOUT TIME ZONE,
    ativo BOOLEAN NOT NULL
);

CREATE TABLE usuarios (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    senha_hash VARCHAR(255) NOT NULL,
    telefone VARCHAR(255),
    data_nascimento DATE,
    tipo_membro VARCHAR(50),
    role VARCHAR(50) NOT NULL,
    plano_id BIGINT,
    data_cadastro TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    ultimo_login TIMESTAMP WITHOUT TIME ZONE,
    ativo BOOLEAN NOT NULL,
    CONSTRAINT fk_usuarios_plano FOREIGN KEY (plano_id) REFERENCES planos (id)
);

CREATE TABLE preferencias_usuarios (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    tipo_preferencia VARCHAR(255) NOT NULL,
    detalhe_preferencia VARCHAR(255) NOT NULL,
    criado_em TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    atualizado_em TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT fk_preferencias_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE CASCADE
);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    token VARCHAR(255) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    expires_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    revoked BOOLEAN NOT NULL,
    CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES usuarios (id) ON DELETE CASCADE
);

CREATE TABLE agendamentos (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    data_horario TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    tipo_atendimento VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    servico VARCHAR(255),
    criado_em TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    atualizado_em TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    notes VARCHAR(2048),
    CONSTRAINT fk_agendamentos_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE CASCADE
);

CREATE TABLE notificacoes (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT,
    titulo VARCHAR(255) NOT NULL,
    mensagem VARCHAR(2048) NOT NULL,
    tipo VARCHAR(50) NOT NULL,
    lida BOOLEAN NOT NULL,
    enviada_em TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT fk_notificacoes_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE SET NULL
);

CREATE TABLE pagamentos (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    valor NUMERIC(10, 2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    metodo_pagamento VARCHAR(255),
    data_pagamento TIMESTAMP WITHOUT TIME ZONE,
    descricao VARCHAR(255),
    CONSTRAINT fk_pagamentos_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE CASCADE
);

CREATE TABLE feedbacks (
    id BIGSERIAL PRIMARY KEY,
    agendamento_id BIGINT NOT NULL,
    usuario_id BIGINT NOT NULL,
    nota INTEGER NOT NULL,
    comentario VARCHAR(2048),
    criado_em TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT fk_feedbacks_agendamento FOREIGN KEY (agendamento_id) REFERENCES agendamentos (id) ON DELETE CASCADE,
    CONSTRAINT fk_feedbacks_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE CASCADE
);

CREATE TABLE community_posts (
    id BIGSERIAL PRIMARY KEY,
    author_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    content VARCHAR(4096) NOT NULL,
    image_url VARCHAR(1024),
    image_base64 TEXT,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT fk_community_posts_author FOREIGN KEY (author_id) REFERENCES usuarios (id) ON DELETE CASCADE
);

CREATE TABLE community_comments (
    id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    content VARCHAR(2048) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT fk_community_comments_post FOREIGN KEY (post_id) REFERENCES community_posts (id) ON DELETE CASCADE,
    CONSTRAINT fk_community_comments_author FOREIGN KEY (author_id) REFERENCES usuarios (id) ON DELETE CASCADE
);

CREATE TABLE community_likes (
    id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT fk_community_likes_post FOREIGN KEY (post_id) REFERENCES community_posts (id) ON DELETE CASCADE,
    CONSTRAINT fk_community_likes_user FOREIGN KEY (user_id) REFERENCES usuarios (id) ON DELETE CASCADE,
    CONSTRAINT uk_community_like UNIQUE (post_id, user_id)
);

CREATE TABLE recomendacoes (
    id BIGSERIAL PRIMARY KEY,
    titulo VARCHAR(255) NOT NULL,
    descricao VARCHAR(2048),
    url VARCHAR(1024),
    status VARCHAR(50) NOT NULL,
    enviada_por BIGINT,
    aprovada_por BIGINT,
    enviada_em TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    atualizada_em TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT fk_recomendacoes_enviada_por FOREIGN KEY (enviada_por) REFERENCES usuarios (id) ON DELETE SET NULL,
    CONSTRAINT fk_recomendacoes_aprovada_por FOREIGN KEY (aprovada_por) REFERENCES admins (id) ON DELETE SET NULL
);
