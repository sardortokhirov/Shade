-- Admin-granted extra withdrawal quota (in addition to earned quota)
-- Nullable + DEFAULT 0 so existing rows get 0 and no NOT NULL constraint failure (Hibernate or Flyway)
ALTER TABLE user_wallet_quota ADD COLUMN IF NOT EXISTS bonus_quota BIGINT DEFAULT 0;
UPDATE user_wallet_quota SET bonus_quota = 0 WHERE bonus_quota IS NULL;
