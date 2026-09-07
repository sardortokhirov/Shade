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
public class UserDTO {
    private Long chatId;
    private String language;
    private String phoneNumber;
    private Boolean isBlocked;
    private BigDecimal balance;
    private Long tickets;
    private Long walletBalance;
    private LocalDateTime registeredAt;
    private Long permanentLimitIncrease;
    private Long effectiveDailyLimit;
    private Long availableLimit;
    private List<String> platformsUsed;
}
