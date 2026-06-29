-- Fix bonus_auto_approve_enabled when column exists with NULLs (e.g. added by Hibernate without default)
UPDATE feature_settings SET bonus_auto_approve_enabled = false WHERE bonus_auto_approve_enabled IS NULL;
