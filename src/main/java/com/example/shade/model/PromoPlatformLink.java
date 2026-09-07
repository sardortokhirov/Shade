package com.example.shade.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "promo_platform_link")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromoPlatformLink {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(name = "platform_user_id", nullable = false, length = 64)
    private String platformUserId;

    @Column(name = "platform_name", nullable = false, length = 80)
    private String platformName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now(ZoneId.of("GMT+5"));
        }
    }
}
