package com.example.shade.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "apk_link_group_cooldown")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApkLinkGroupCooldown {

    @Id
    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(name = "last_request_at", nullable = false)
    private Instant lastRequestAt;
}
