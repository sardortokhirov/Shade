package com.example.shade.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class LotteryPrizeRequest {
    private String name;

    /**
     * Amount as String to support very large numbers (avoids JavaScript number precision issues).
     * Will be converted to BigDecimal in the controller.
     */
    @JsonProperty("amount")
    private String amount;

    @JsonProperty("numberOfPrize")
    private Integer numberOfPrize;
}
