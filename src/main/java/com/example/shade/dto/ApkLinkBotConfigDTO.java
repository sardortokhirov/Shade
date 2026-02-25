package com.example.shade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApkLinkBotConfigDTO {
    private String botTokenMasked;
    private Integer cooldownPrivateMinutes;
    private Integer cooldownGroupMinutes;
    private String channelKeywordAllApk;
    private String groupKeywordAllApk;
    private Long apkChannelChatId;
    private Integer apkChannelMessageId;
    private String apkChannelMessageLink;
    private Long mainApkChannelChatId;
    private Integer autoPostIntervalHours;
    private java.time.Instant lastAutoPostTime;
}
