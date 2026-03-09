-- Admin-granted extra withdrawal quota (in addition to earned quota)
ALTER TABLE user_wallet_quota ADD COLUMN bonus_quota BIGINT NOT NULL DEFAULT 0;
