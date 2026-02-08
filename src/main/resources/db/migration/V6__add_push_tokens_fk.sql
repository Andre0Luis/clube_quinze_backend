-- Add FK for push_tokens.user_id -> usuarios.id
ALTER TABLE push_tokens
    ADD CONSTRAINT fk_push_token_user
    FOREIGN KEY (user_id) REFERENCES usuarios(id);
