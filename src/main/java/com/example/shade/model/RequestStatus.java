package com.example.shade.model;

/**
 * Date-6/14/2025
 * By Sardor Tokhirov
 * Time-4:11 PM (GMT+5)
 */
public enum RequestStatus {
    PENDING,
    PENDING_SMS,
    PENDING_ADMIN,
    APPROVED,
    BONUS_APPROVED,
    CANCELED,
    PENDING_PAYMENT,
    FAILED,
    /** Wallet-to-platform transfer failed; admin chose to refund user's wallet. */
    FAILED_REFUNDED,
    USER_CANCELED,
    PENDING_SCREENSHOT,
    PROCESSING
}