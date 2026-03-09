package com.example.shade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response for dashboard API: all users' wallet balances and total.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserWalletBalancesResponse {

    /** Sum of all users' wallet balances (UZS). */
    private long totalWalletMoney;

    /** Per-user wallet balance: chatId and walletBalance (UZS). */
    private List<UserWalletBalanceItem> userBalances;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserWalletBalanceItem {
        private Long chatId;
        private Long walletBalance;
    }
}
