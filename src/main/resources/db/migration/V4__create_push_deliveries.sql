-- Create push_deliveries table
CREATE TABLE IF NOT EXISTS push_deliveries (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    token_id BIGINT,
    user_id BIGINT,
    appointment_id BIGINT,
    kind VARCHAR(50),
    title VARCHAR(255),
    body TEXT,
    data TEXT,
    status VARCHAR(50),
    error_message TEXT,
    retry_count INT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP NULL,
    CONSTRAINT fk_push_delivery_token FOREIGN KEY (token_id) REFERENCES push_tokens(id)
);

CREATE INDEX idx_push_deliveries_user_id ON push_deliveries(user_id);
CREATE INDEX idx_push_deliveries_token_id ON push_deliveries(token_id);
