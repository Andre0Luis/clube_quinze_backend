-- Create push_tokens table
CREATE TABLE IF NOT EXISTS push_tokens (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token VARCHAR(255) NOT NULL,
    platform VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_success_at TIMESTAMP NULL,
    invalidated_at TIMESTAMP NULL
);

CREATE INDEX idx_push_tokens_user_id ON push_tokens(user_id);
CREATE UNIQUE INDEX idx_push_tokens_token ON push_tokens(token);
