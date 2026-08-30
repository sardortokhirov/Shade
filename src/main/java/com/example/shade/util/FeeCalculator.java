package com.example.shade.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Shared fee math for wallet P2P and lottery ticket trades.
 * fee = floor(amount * percent), net = amount - fee.
 */
public final class FeeCalculator {
    private FeeCalculator() {
    }

    public static long feeAmount(long amount, BigDecimal percentage) {
        if (amount < 0) {
            throw new IllegalArgumentException("Amount must be non-negative");
        }
        BigDecimal pct = percentage != null ? percentage : BigDecimal.ZERO;
        if (pct.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Fee percentage must be non-negative");
        }
        long fee = BigDecimal.valueOf(amount)
                .multiply(pct)
                .setScale(0, RoundingMode.DOWN)
                .longValue();
        if (fee > amount) {
            throw new IllegalArgumentException("Fee exceeds amount");
        }
        return fee;
    }

    public static long netAmount(long amount, BigDecimal percentage) {
        return amount - feeAmount(amount, percentage);
    }
}
