package com.example.shade.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserDetailDTO {
    private Long chatId;
    private String language;
    private String phoneNumber;
    private Boolean isBlocked;
    private BigDecimal balance;
    private Long tickets;
    private LocalDateTime registeredAt;
    private Long permanentLimitIncrease; // Rounded value
    private BigDecimal permanentLimitIncreasePrecise; // Precise decimal value with 8 decimal places
    private String permanentLimitIncreaseFormatted; // Formatted string with 8 decimal places
    private LocalDateTime permanentLimitLastUpdated; // Last update time from user_limit_increase table
    private Long effectiveDailyLimit;
    private Long availableLimit;
    private List<String> platformsUsed;
    private Long dailyTopUpAmount;
    private Long dailyTransferAmount;
    private Long dailyLimitIncrease;
    private LocalDate dailyStatsDate; // Date of daily stats record
    private LocalDateTime dailyStatsLastUpdated; // Last update time from daily_user_stats table
    private LocalDateTime lastLotteryPlayTime;
    private LocalDateTime lastUpdated; // General last updated (from daily stats)
    
    // Detailed limit breakdown for admin
    private Long baseDailyLimit;
    private String limitBreakdown; // Human-readable breakdown
}
