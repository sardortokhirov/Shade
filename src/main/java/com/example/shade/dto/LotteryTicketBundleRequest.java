package com.example.shade.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class LotteryTicketBundleRequest {
    @JsonProperty("tickets")
    private Long tickets; // Maps to ticketQuantity in entity

    private BigDecimal price;

    @JsonProperty("currency")
    private String currency; // Ignored, but accepted in request

    @JsonProperty("displayOrder")
    private Integer displayOrder;

    @JsonProperty("isActive")
    private Boolean isActive;
}
