package com.example.shade.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BaseDailyLimitRequest {
    /** Base daily limit as percentage of system dailyBonusTransferLimit (e.g. 100 = 100%, 150 = 150%). */
    private Integer percentage;
}
