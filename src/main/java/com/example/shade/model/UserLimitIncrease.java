package com.example.shade.model;

import jakarta.persistence.*;
import lombok.*;

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

    @Column(name = "accumulated_limit_increase", nullable = false)
    @Builder.Default
    private Long accumulatedLimitIncrease = 0L;

    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;
}
