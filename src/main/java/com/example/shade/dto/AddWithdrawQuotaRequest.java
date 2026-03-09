package com.example.shade.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AddWithdrawQuotaRequest {
    /** Amount (UZS) to add to the user's bonus withdrawal quota. */
    private Long amount;
}
