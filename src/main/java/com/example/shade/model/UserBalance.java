package com.example.shade.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_balance")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserBalance {
    @Id
    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(name = "tickets", nullable = false)
    private Long tickets;

    @Column(name = "balance", nullable = false, precision = 30, scale = 2)
    private BigDecimal balance;

    @Column(name = "last_lottery_play_time", nullable = true)
    private LocalDateTime lastLotteryPlayTime;
}