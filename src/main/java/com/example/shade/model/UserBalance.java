package com.example.shade.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

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

    @Column(name = "balance", nullable = false)
    private BigDecimal balance;

    @Column(name = "wallet_balance", columnDefinition = "bigint default 0")
    @Builder.Default
    private Long walletBalance = 0L;
}