package com.example.shade.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "apk_link_bot_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApkLinkBotConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bot_token", length = 512)
    private String botToken;

    @Column(name = "cooldown_private_minutes")
    private Integer cooldownPrivateMinutes;

    @Column(name = "cooldown_group_minutes")
    private Integer cooldownGroupMinutes;
}
