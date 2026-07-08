ALTER TABLE hizmat_request
    ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP;

UPDATE hizmat_request
SET approved_at = created_at
WHERE status = 'BONUS_APPROVED'
  AND approved_at IS NULL;
