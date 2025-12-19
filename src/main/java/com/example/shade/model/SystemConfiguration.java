package com.example.shade.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
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

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
