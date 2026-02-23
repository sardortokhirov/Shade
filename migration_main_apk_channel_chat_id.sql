-- Main APK channel: only this channel triggers send-all-APKs and link update.
-- Run manually if not using Hibernate ddl-auto=update.

ALTER TABLE apk_link_bot_config
ADD COLUMN IF NOT EXISTS main_apk_channel_chat_id BIGINT;
