package com.example.shade.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPlatformPermission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String userId;

    @Builder.Default
    private boolean canTopUp = true;
    @Builder.Default
    private boolean canWithdraw = true;
    @Builder.Default
    private boolean canBonusTopUp = true;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now(ZoneId.of("GMT+5"));
    }
}
