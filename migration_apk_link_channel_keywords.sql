-- APK Link Bot: channel/group keywords and stored APK message link
-- Run manually if not using Hibernate schema update.

ALTER TABLE apk_link_bot_config
    ADD COLUMN IF NOT EXISTS channel_keyword_all_apk VARCHAR(255),
    ADD COLUMN IF NOT EXISTS group_keyword_all_apk VARCHAR(255),
    ADD COLUMN IF NOT EXISTS apk_channel_chat_id BIGINT,
    ADD COLUMN IF NOT EXISTS apk_channel_message_id INT;

ALTER TABLE apk_link_platform
    ADD COLUMN IF NOT EXISTS apk_file_name VARCHAR(512);
