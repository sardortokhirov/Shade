package com.example.shade.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO for daily user statistics
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyUserStatsDTO {
    private LocalDate date;
    private Long dailyTopUpAmount;
    private Long dailyTransferAmount;
    private Long dailyLimitIncrease;
    private Long carryoverAmount;
    private LocalDateTime lastUpdated;
}
