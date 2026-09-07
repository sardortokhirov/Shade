package com.example.shade.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "apk_link_group_platform_cooldown", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "chat_id", "platform_id" })
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApkLinkGroupPlatformCooldown {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(name = "platform_id", nullable = false)
    private Long platformId;

    @Column(name = "last_request_at", nullable = false)
    private Instant lastRequestAt;
}
