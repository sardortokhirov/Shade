ALTER TABLE apk_link_bot_config ADD COLUMN auto_post_interval_hours INTEGER;
ALTER TABLE apk_link_bot_config ADD COLUMN last_auto_post_time TIMESTAMP;
ALTER TABLE apk_link_platform ADD COLUMN link_keyword VARCHAR(255);
ALTER TABLE apk_link_platform ADD COLUMN apk_keyword VARCHAR(255);
