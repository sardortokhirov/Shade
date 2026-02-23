-- APK/Link distribution bot: 5 new tables (optional; project uses JPA ddl-auto=update)
-- Run manually if not using Hibernate schema update.

CREATE TABLE IF NOT EXISTS apk_link_bot_config (
    id BIGSERIAL PRIMARY KEY,
    bot_token VARCHAR(512),
    cooldown_private_minutes INT,
    cooldown_group_minutes INT
);

CREATE TABLE IF NOT EXISTS apk_link_platform (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    link_url VARCHAR(1024) NOT NULL,
    apk_file_id VARCHAR(512),
    apk_url VARCHAR(1024),
    sort_order INT
);

CREATE TABLE IF NOT EXISTS apk_link_keyword (
    id BIGSERIAL PRIMARY KEY,
    platform_id BIGINT NOT NULL REFERENCES apk_link_platform(id) ON DELETE CASCADE,
    keyword VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS apk_link_user_cooldown (
    user_id BIGINT PRIMARY KEY,
    last_request_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS apk_link_group_cooldown (
    chat_id BIGINT PRIMARY KEY,
    last_request_at TIMESTAMP NOT NULL
);
