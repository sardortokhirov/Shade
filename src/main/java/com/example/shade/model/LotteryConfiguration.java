package com.example.shade.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Lottery configuration entity for storing lottery-specific settings
 * Date: Current
 * By: System
 */
@Entity
@Table(name = "lottery_configuration")
@Data
public class LotteryConfiguration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "purchase_cooldown_seconds", nullable = false)
    private Long purchaseCooldownSeconds = 300L;

    @Column(name = "winnings_percentage", nullable = false, precision = 9, scale = 8)
    @JsonSerialize(using = BigDecimalPlainSerializer.class)
    private BigDecimal winningsPercentage = BigDecimal.ZERO;

    /** Minimum UZS price per ticket for player-to-player lottery trade listings. */
    @Column(name = "p2p_min_price_per_ticket", nullable = false, columnDefinition = "bigint default 1")
    private Long p2pMinPricePerTicket = 1L;

    /**
     * Fee fraction for lottery ticket trades (e.g. 0.05 = 5%).
     * Deducted from the listing total; seller receives the remainder in wallet.
     */
    @Column(name = "p2p_fee_percentage", nullable = false, precision = 9, scale = 8,
            columnDefinition = "numeric(9,8) default 0")
    @JsonSerialize(using = BigDecimalPlainSerializer.class)
    private BigDecimal p2pFeePercentage = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
