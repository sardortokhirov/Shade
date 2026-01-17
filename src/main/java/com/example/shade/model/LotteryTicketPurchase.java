package com.example.shade.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "lottery_ticket_purchase")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LotteryTicketPurchase {
    @Id
    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(name = "last_purchase_time", nullable = false)
    private LocalDateTime lastPurchaseTime;
}
