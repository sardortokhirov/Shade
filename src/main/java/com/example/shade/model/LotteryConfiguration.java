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

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
