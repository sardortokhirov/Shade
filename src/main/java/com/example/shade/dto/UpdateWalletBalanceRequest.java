package com.example.shade.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateWalletBalanceRequest {
    /** New wallet balance in UZS (replaces current value). Must be >= 0. */
    private Long walletBalance;
}
