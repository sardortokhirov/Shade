package com.example.shade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Frontend-friendly status for tip bonus configuration.
 * Use GET /api/bot-tip-config/status to retrieve.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BotTipConfigStatusDTO {

    /** Whether bonus tickets are enabled. */
    private boolean bonusTicketsEnabled;

    /** Minimum tickets in random range (inclusive). */
    private long minBonusTickets;

    /** Maximum tickets in random range (inclusive). */
    private long maxBonusTickets;

    /** Chance (0-100) to award bonus. 100 = always when enabled. */
    private int bonusTicketsChance;

    /** "enabled" or "disabled" for quick checks. */
    private String statusCode;

    /** Human-readable description for UI display. */
    private String statusDescription;

    /** Example: "1-10 tickets, 100% chance" or "Disabled" */
    private String bonusRangeSummary;

    /** Whether tip awards permanent limit increase (doimiy limit). */
    private boolean tipLimitIncreaseEnabled;

    /** Per this many UZS tipped (e.g. 1000). */
    private Long tipLimitPerAmountUzs;

    /** Add this many UZS permanent limit (e.g. 50). */
    private Long tipLimitAmountUzs;

    /** Example: "Per 1000 UZS tip → 50 UZS limit" or "Disabled" */
    private String tipLimitSummary;
}
