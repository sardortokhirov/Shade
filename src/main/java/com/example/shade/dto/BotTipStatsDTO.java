package com.example.shade.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BotTipStatsDTO {
    private long totalTipsCount;
    private double totalTipsAmount;
    private Map<String, Long> tipsCountByDate;
    private Map<String, Double> tipsAmountByDate;
}
