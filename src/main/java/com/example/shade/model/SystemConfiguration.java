package com.example.shade.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * System configuration entity for storing dynamic business rules
 * Date: Current
 * By: System
 */
@Entity
@Table(name = "system_configuration")
@Data
public class SystemConfiguration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "top_up_min_amount", nullable = false)
    private Long topUpMinAmount = 5_000L;

    @Column(name = "top_up_max_amount", nullable = false)
    private Long topUpMaxAmount = 10_000_000L;

    @Column(name = "bonus_top_up_min_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal bonusTopUpMinAmount = new BigDecimal("3600");

    @Column(name = "bonus_top_up_max_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal bonusTopUpMaxAmount = new BigDecimal("100000");

    @Column(name = "min_tickets", nullable = false)
    private Long minTickets = 5L;

    @Column(name = "max_tickets", nullable = false)
    private Long maxTickets = 400L;

    @Column(name = "withdrawal_commission_percentage", nullable = false, precision = 5, scale = 4)
    private BigDecimal withdrawalCommissionPercentage = BigDecimal.ZERO;

    @Column(name = "referral_commission_percentage", nullable = false, precision = 5, scale = 4)
    private BigDecimal referralCommissionPercentage = new BigDecimal("0.001");

    @Column(name = "ticket_calculation_amount", nullable = false)
    private Long ticketCalculationAmount = 10_000L;

    @Column(name = "daily_bonus_transfer_limit", nullable = false)
    private Long dailyBonusTransferLimit = 100_000L;

    @Column(name = "top_up_daily_limit_increase_percentage", nullable = false, precision = 9, scale = 8)
    @JsonSerialize(using = BigDecimalPlainSerializer.class)
    private BigDecimal topUpDailyLimitIncreasePercentage = BigDecimal.ZERO;

    @Column(name = "deposit_daily_limit_increase_percentage", nullable = false, precision = 9, scale = 8)
    @JsonSerialize(using = BigDecimalPlainSerializer.class)
    private BigDecimal depositDailyLimitIncreasePercentage = BigDecimal.ZERO;

    @Column(name = "humo_enabled", nullable = false)
    private Boolean humoEnabled = true;

    /**
     * Global UZ top-up mode: Oson lane, CardXabar lane, or off (no UZ cards in rotation).
     * Nullable in DB (null = treat as OSON in {@link #normalizeUzcardRailDefault}).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "uzcard_rail", length = 32)
    private UzcardRail uzcardRail = UzcardRail.OSON;

    /**
     * When null: HUMO cards use legacy CardXabar-then-HUMO check (current production default).
     * When set: dual check applies only while {@code now() < this instant}; after that, HUMO-only on 2805.
     */
    @Column(name = "humo_legacy_dual_check_end")
    private Instant humoLegacyDualCheckEnd;

    @Column(name = "lottery_cooldown_seconds", nullable = false)
    private Long lotteryCooldownSeconds = 300L;

    @Column(name = "wallet_min_withdraw_amount", nullable = false, columnDefinition = "bigint default 10000")
    private Long walletMinWithdrawAmount = 10_000L;

    /**
     * Multiplier: how many UZS of withdrawal quota a user earns per 1 UZS
     * transferred to a platform.
     * E.g., ratio=10 means sending 10,000 to platform earns 100,000 withdrawal
     * quota.
     * Applies only to new platform transfers after each ratio change.
     */
    @Column(name = "wallet_withdraw_ratio", nullable = false, columnDefinition = "bigint default 10")
    private Long walletWithdrawRatio = 10L;

    /**
     * Wallet -> Platform transfer amount limits (independent from card top-up limits).
     */
    @Column(name = "wallet_transfer_min_amount", columnDefinition = "bigint default 5000")
    private Long walletTransferMinAmount = 5_000L;

    @Column(name = "wallet_transfer_max_amount", columnDefinition = "bigint default 10000000")
    private Long walletTransferMaxAmount = 10_000_000L;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PostLoad
    void normalizeUzcardRailDefault() {
        if (uzcardRail == null) {
            uzcardRail = UzcardRail.OSON;
        }
    }
}
