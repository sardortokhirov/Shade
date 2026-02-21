package com.example.shade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BaseDailyLimitUpdateResponse {
    private Long baseDailyLimit;
    private Long effectiveDailyLimit;
    private LocalDateTime lastUpdated;
}
