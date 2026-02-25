package com.example.shade.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApkLinkBotConfigRequest {
    private String botToken;
    private Integer cooldownPrivateMinutes;
    private Integer cooldownGroupMinutes;
    private String channelKeywordAllApk;
    private String groupKeywordAllApk;
    private Integer autoPostIntervalHours;
}
