package com.example.shade.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BaseDailyLimitRequest {
    /** Per-user base daily limit in UZS (e.g. 5_000_000). Must be non-negative. */
    private Long baseDailyLimit;
}
