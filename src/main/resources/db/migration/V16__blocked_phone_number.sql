CREATE TABLE blocked_phone_number (
    normalized_phone VARCHAR(32) PRIMARY KEY,
    linked_chat_id BIGINT
);

CREATE INDEX idx_blocked_phone_linked_chat ON blocked_phone_number (linked_chat_id);
