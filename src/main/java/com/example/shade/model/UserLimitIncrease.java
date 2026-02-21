package com.example.shade.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_limit_increase")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserLimitIncrease {
    @Id
    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(name = "accumulated_limit_increase", nullable = false, precision = 20, scale = 8)
    @Builder.Default
    private BigDecimal accumulatedLimitIncrease = BigDecimal.ZERO;

    /** Per-user base daily limit override (UZS). If null, system default is used. */
    @Column(name = "base_daily_limit_override", nullable = true)
    private Long baseDailyLimitOverride;

    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;
}
