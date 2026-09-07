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
    /** Calculated base daily limit (system limit * percentage / 100) for display. */
    private Long baseDailyLimit;
    private Long effectiveDailyLimit;
    private LocalDateTime lastUpdated;
    /** Stored percentage (e.g. 100, 150). */
    private Integer percentage;
}
