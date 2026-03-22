-- Links each pending blur row to the HizmatRequest it was created for (avoids duplicate screenshot after status changed).
ALTER TABLE pending_payment_message
    ADD COLUMN hizmat_request_id BIGINT NULL;
