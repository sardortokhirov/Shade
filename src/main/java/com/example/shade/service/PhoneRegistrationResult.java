package com.example.shade.service;

public enum PhoneRegistrationResult {
    /** New UserBalance row was created. */
    ACCEPTED_NEW_BALANCE,
    /** User already had balance; phone updated. */
    ACCEPTED_EXISTING_BALANCE,
    /** Phone is on the global blocklist; nothing was persisted. */
    REJECTED_PHONE_BLOCKED
}
