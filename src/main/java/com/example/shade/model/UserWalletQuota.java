package com.example.shade.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Tracks per-user wallet withdrawal quota.
 *
 * Earned quota = sum of (platform transfers * walletWithdrawRatio at time of
 * transfer)
 * Used quota = sum of approved wallet-to-card withdrawals
 * Remaining = earned - used
 *
 * Only applies to wallet→card withdrawals (WALLET_WITHDRAWAL type).
 * Quota is permanent and never resets.
 */
@Entity
@Table(name = "user_wallet_quota")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserWalletQuota {

    @Id
    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    /** Total withdrawal quota earned through platform deposits. */
    @Column(name = "earned_quota", nullable = false)
    @Builder.Default
    private Long earnedQuota = 0L;

    /** Total quota already used by approved wallet-to-card withdrawals. */
    @Column(name = "used_quota", nullable = false)
    @Builder.Default
    private Long usedQuota = 0L;

    /** Remaining available quota for withdrawals. */
    public Long getRemainingQuota() {
        return Math.max(0L, earnedQuota - usedQuota);
    }
}
