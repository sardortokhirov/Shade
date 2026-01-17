package com.example.shade.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class LotteryConfigService {
    // Default values
    private static final Long DEFAULT_PURCHASE_COOLDOWN_SECONDS = 300L;
    private static final BigDecimal DEFAULT_WINNINGS_PERCENTAGE = BigDecimal.ZERO;

    // In-memory storage (can be replaced with database storage if needed)
    private AtomicLong purchaseCooldownSeconds = new AtomicLong(DEFAULT_PURCHASE_COOLDOWN_SECONDS);
    private AtomicReference<BigDecimal> winningsPercentage = new AtomicReference<>(DEFAULT_WINNINGS_PERCENTAGE);

    public Long getPurchaseCooldownSeconds() {
        return purchaseCooldownSeconds.get();
    }

    public void setPurchaseCooldownSeconds(Long seconds) {
        if (seconds == null || seconds < 0) {
            throw new IllegalArgumentException("Cooldown seconds must be non-negative");
        }
        this.purchaseCooldownSeconds.set(seconds);
    }

    public BigDecimal getWinningsPercentage() {
        return winningsPercentage.get();
    }

    public void setWinningsPercentage(BigDecimal percentage) {
        if (percentage == null || percentage.compareTo(BigDecimal.ZERO) < 0 || percentage.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Winnings percentage must be between 0 and 100");
        }
        this.winningsPercentage.set(percentage);
    }
}
