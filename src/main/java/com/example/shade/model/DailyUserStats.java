package com.example.shade.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entity for tracking daily user statistics (top-ups and bonus transfers)
 */
@Entity
@Table(name = "daily_user_stats", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"chat_id", "date"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyUserStats {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "daily_top_up_amount", nullable = false)
    private Long dailyTopUpAmount = 0L;

    @Column(name = "daily_transfer_amount", nullable = false)
    private Long dailyTransferAmount = 0L;

    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;
}
