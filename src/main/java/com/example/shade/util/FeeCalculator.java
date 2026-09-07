package com.example.shade.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Shared fee math for wallet P2P.
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
        if (pct.compareTo(BigDecimal.ZERO) < 0 || pct.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Fee percentage must be between 0 and 1");
        }
        long fee;
        try {
            fee = BigDecimal.valueOf(amount)
                    .multiply(pct)
                    .setScale(0, RoundingMode.DOWN)
                    .longValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Fee overflow", e);
        }
        if (fee < 0 || fee > amount) {
            throw new IllegalArgumentException("Fee exceeds amount");
        }
        return fee;
    }

    public static long netAmount(long amount, BigDecimal percentage) {
        return amount - feeAmount(amount, percentage);
    }
}
