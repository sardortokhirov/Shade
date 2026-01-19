package com.example.shade.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
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
    private Long permanentLimitIncrease;
    private BigDecimal permanentLimitIncreasePrecise; // Precise decimal value
    private Long effectiveDailyLimit;
    private Long availableLimit;
    private List<String> platformsUsed;
    private Long dailyTopUpAmount;
    private Long dailyTransferAmount;
    private Long dailyLimitIncrease;
    private LocalDateTime lastLotteryPlayTime;
    private LocalDateTime lastUpdated;
    
    // Detailed limit breakdown for admin
    private Long baseDailyLimit;
    private String limitBreakdown; // Human-readable breakdown
}
