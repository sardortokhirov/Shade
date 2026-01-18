package com.example.shade.service;

import com.example.shade.model.LotteryConfiguration;
import com.example.shade.repository.LotteryConfigurationRepository;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class LotteryConfigService {
    private static final Logger logger = LoggerFactory.getLogger(LotteryConfigService.class);
    private final LotteryConfigurationRepository configurationRepository;

    // Default values
    private static final Long DEFAULT_PURCHASE_COOLDOWN_SECONDS = 300L;
    private static final BigDecimal DEFAULT_WINNINGS_PERCENTAGE = BigDecimal.ZERO;

    @PostConstruct
    public void init() {
        // Ensure default configuration exists on startup
        getConfiguration();
        logger.info("Lottery configuration initialized");
    }

    @Transactional
    public LotteryConfiguration getConfiguration() {
        return configurationRepository.findLatest()
                .orElseGet(() -> {
                    LotteryConfiguration config = new LotteryConfiguration();
                    config.setPurchaseCooldownSeconds(DEFAULT_PURCHASE_COOLDOWN_SECONDS);
                    config.setWinningsPercentage(DEFAULT_WINNINGS_PERCENTAGE);
                    config.setCreatedAt(LocalDateTime.now(ZoneId.of("GMT+5")));
                    LotteryConfiguration saved = configurationRepository.save(config);
                    logger.info("Created default lottery configuration: {}", saved.getId());
                    return saved;
                });
    }

    public Long getPurchaseCooldownSeconds() {
        LotteryConfiguration config = getConfiguration();
        return config.getPurchaseCooldownSeconds() != null 
                ? config.getPurchaseCooldownSeconds() 
                : DEFAULT_PURCHASE_COOLDOWN_SECONDS;
    }

    @Transactional
    public void setPurchaseCooldownSeconds(Long seconds) {
        if (seconds == null || seconds < 0) {
            throw new IllegalArgumentException("Cooldown seconds must be non-negative");
        }
        LotteryConfiguration current = getConfiguration();
        LotteryConfiguration config = new LotteryConfiguration();
        config.setPurchaseCooldownSeconds(seconds);
        config.setWinningsPercentage(current.getWinningsPercentage());
        config.setCreatedAt(LocalDateTime.now(ZoneId.of("GMT+5")));
        LotteryConfiguration saved = configurationRepository.save(config);
        logger.info("Updated purchase cooldown seconds to {}: {}", seconds, saved.getId());
    }

    public BigDecimal getWinningsPercentage() {
        LotteryConfiguration config = getConfiguration();
        return config.getWinningsPercentage() != null 
                ? config.getWinningsPercentage() 
                : DEFAULT_WINNINGS_PERCENTAGE;
    }

    @Transactional
    public void setWinningsPercentage(BigDecimal percentage) {
        if (percentage == null || percentage.compareTo(BigDecimal.ZERO) < 0 || percentage.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Winnings percentage must be between 0 and 100");
        }
        LotteryConfiguration current = getConfiguration();
        LotteryConfiguration config = new LotteryConfiguration();
        config.setPurchaseCooldownSeconds(current.getPurchaseCooldownSeconds());
        config.setWinningsPercentage(percentage);
        config.setCreatedAt(LocalDateTime.now(ZoneId.of("GMT+5")));
        LotteryConfiguration saved = configurationRepository.save(config);
        logger.info("Updated winnings percentage to {}: {}", percentage, saved.getId());
    }
}
