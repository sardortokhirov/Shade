package com.example.shade.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "allowed_promo_users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AllowedPromoUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = true, unique = false)
    private String userId;

    @Column(nullable = true, unique = false)
    private Long chatId;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now(ZoneId.of("GMT+5"));
    }
}
