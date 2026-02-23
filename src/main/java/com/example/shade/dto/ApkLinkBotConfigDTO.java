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
}
