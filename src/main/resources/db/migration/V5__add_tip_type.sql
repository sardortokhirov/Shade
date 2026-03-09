-- Drop the old type check constraint that doesn't include TIP
ALTER TABLE hizmat_request DROP CONSTRAINT IF EXISTS hizmat_request_type_check;

-- Recreate with all current RequestType enum values including TIP
ALTER TABLE hizmat_request ADD CONSTRAINT hizmat_request_type_check
    CHECK (type IN ('TOP_UP', 'WITHDRAWAL', 'WALLET_DEPOSIT', 'WALLET_WITHDRAWAL', 'WALLET_TO_PLATFORM', 'TIP'));
