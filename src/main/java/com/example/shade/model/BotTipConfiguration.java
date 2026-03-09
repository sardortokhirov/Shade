package com.example.shade.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * System configuration entity for storing bot tip presets and limits.
 */
@Entity
@Table(name = "bot_tip_configuration")
@Data
public class BotTipConfiguration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "presets", nullable = false)
    private String presets = "5000,10000,20000";

    @Column(name = "min_amount", nullable = false)
    private Long minAmount = 5000L;

    @Column(name = "min_bonus_tickets", nullable = false)
    private Long minBonusTickets = 0L;

    @Column(name = "max_bonus_tickets", nullable = false)
    private Long maxBonusTickets = 0L;

    /** When false, no bonus tickets are awarded regardless of range. */
    @Column(name = "bonus_tickets_enabled", nullable = false)
    private Boolean bonusTicketsEnabled = true;

    /** Chance (0-100) to award bonus tickets. 100 = always when enabled. */
    @Column(name = "bonus_tickets_chance", nullable = false)
    private Integer bonusTicketsChance = 100;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
