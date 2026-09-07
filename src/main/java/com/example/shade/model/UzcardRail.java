package com.example.shade.model;

/**
 * System config: which UZCARD verification path is active, or none.
 * On {@link com.example.shade.model.AdminCard} only {@link #OSON} and {@link #CARDXABAR} are stored ({@link #OFF} is never on a card row).
 */
public enum UzcardRail {
    OSON,
    CARDXABAR,
    /** UZCARD cards are not offered for top-up; Oson and CardXabar both disabled at system level. */
    OFF
}
