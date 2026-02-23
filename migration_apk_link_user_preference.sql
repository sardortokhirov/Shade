-- APK Link Bot: user language preference
CREATE TABLE IF NOT EXISTS apk_link_user_preference (
    chat_id BIGINT PRIMARY KEY,
    language_code VARCHAR(10) NOT NULL
);
