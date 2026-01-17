-- Migration script for lottery ticket purchase feature
-- Run this script on your PostgreSQL database

-- Create lottery_ticket_bundle table
CREATE TABLE IF NOT EXISTS lottery_ticket_bundle (
    id BIGSERIAL PRIMARY KEY,
    ticket_quantity BIGINT NOT NULL,
    price DECIMAL(19, 2) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create lottery_ticket_purchase table
CREATE TABLE IF NOT EXISTS lottery_ticket_purchase (
    chat_id BIGINT PRIMARY KEY,
    last_purchase_time TIMESTAMP NOT NULL
);

-- Add indexes for better performance
CREATE INDEX IF NOT EXISTS idx_lottery_ticket_bundle_active ON lottery_ticket_bundle(is_active);
CREATE INDEX IF NOT EXISTS idx_lottery_ticket_bundle_display_order ON lottery_ticket_bundle(display_order);
CREATE INDEX IF NOT EXISTS idx_lottery_ticket_purchase_chat_id ON lottery_ticket_purchase(chat_id);
