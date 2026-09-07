package com.example.shade.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "apk_link_user_cooldown")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApkLinkUserCooldown {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "last_request_at", nullable = false)
    private Instant lastRequestAt;
}
