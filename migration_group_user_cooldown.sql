CREATE TABLE apk_link_user_group_stats (
    id SERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    chat_id BIGINT NOT NULL,
    link_count INTEGER DEFAULT 0,
    apk_count INTEGER DEFAULT 0,
    frozen_until TIMESTAMP,
    UNIQUE(user_id, chat_id)
);

ALTER TABLE apk_link_bot_config ADD COLUMN group_user_link_limit INTEGER;
ALTER TABLE apk_link_bot_config ADD COLUMN group_user_apk_limit INTEGER;
ALTER TABLE apk_link_bot_config ADD COLUMN group_user_freeze_minutes INTEGER;
