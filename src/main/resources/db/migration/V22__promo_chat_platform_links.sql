CREATE TABLE IF NOT EXISTS promo_allowed_chat (
    id BIGSERIAL PRIMARY KEY,
    chat_id BIGINT NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS promo_platform_link (
    id BIGSERIAL PRIMARY KEY,
    chat_id BIGINT NOT NULL REFERENCES promo_allowed_chat (chat_id) ON DELETE CASCADE,
    platform_user_id VARCHAR(64) NOT NULL,
    platform_name VARCHAR(80) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_promo_platform_link_chat_user UNIQUE (chat_id, platform_user_id)
);

CREATE INDEX IF NOT EXISTS idx_promo_platform_link_platform_user_id ON promo_platform_link (platform_user_id);

INSERT INTO promo_allowed_chat (chat_id, created_at)
SELECT DISTINCT chat_id, COALESCE(created_at, NOW())
FROM allowed_promo_users
WHERE chat_id IS NOT NULL
ON CONFLICT (chat_id) DO NOTHING;
