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

    @Column(name = "channel_keyword_all_apk", length = 255)
    private String channelKeywordAllApk;

    @Column(name = "group_keyword_all_apk", length = 255)
    private String groupKeywordAllApk;

    @Column(name = "apk_channel_chat_id")
    private Long apkChannelChatId;

    @Column(name = "apk_channel_message_id")
    private Integer apkChannelMessageId;
}
