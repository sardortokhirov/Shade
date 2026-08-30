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

    private static final Long DEFAULT_PURCHASE_COOLDOWN_SECONDS = 300L;
    private static final BigDecimal DEFAULT_WINNINGS_PERCENTAGE = BigDecimal.ZERO;
    private static final Long DEFAULT_P2P_MIN_PRICE_PER_TICKET = 1L;
    private static final BigDecimal DEFAULT_P2P_FEE_PERCENTAGE = BigDecimal.ZERO;

    @PostConstruct
    public void init() {
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
                    config.setP2pMinPricePerTicket(DEFAULT_P2P_MIN_PRICE_PER_TICKET);
                    config.setP2pFeePercentage(DEFAULT_P2P_FEE_PERCENTAGE);
                    config.setCreatedAt(LocalDateTime.now(ZoneId.of("GMT+5")));
                    LotteryConfiguration saved = configurationRepository.save(config);
                    logger.info("Created default lottery configuration: {}", saved.getId());
                    return saved;
                });
    }

    private LotteryConfiguration copyConfig(LotteryConfiguration current) {
        LotteryConfiguration config = new LotteryConfiguration();
        config.setPurchaseCooldownSeconds(current.getPurchaseCooldownSeconds() != null
                ? current.getPurchaseCooldownSeconds()
                : DEFAULT_PURCHASE_COOLDOWN_SECONDS);
        config.setWinningsPercentage(current.getWinningsPercentage() != null
                ? current.getWinningsPercentage()
                : DEFAULT_WINNINGS_PERCENTAGE);
        config.setP2pMinPricePerTicket(current.getP2pMinPricePerTicket() != null
                ? current.getP2pMinPricePerTicket()
                : DEFAULT_P2P_MIN_PRICE_PER_TICKET);
        config.setP2pFeePercentage(current.getP2pFeePercentage() != null
                ? current.getP2pFeePercentage()
                : DEFAULT_P2P_FEE_PERCENTAGE);
        return config;
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
        LotteryConfiguration config = copyConfig(current);
        config.setPurchaseCooldownSeconds(seconds);
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
        if (percentage == null || percentage.compareTo(BigDecimal.ZERO) < 0
                || percentage.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Winnings percentage must be between 0 and 100");
        }
        LotteryConfiguration current = getConfiguration();
        LotteryConfiguration config = copyConfig(current);
        config.setWinningsPercentage(percentage);
        config.setCreatedAt(LocalDateTime.now(ZoneId.of("GMT+5")));
        LotteryConfiguration saved = configurationRepository.save(config);
        logger.info("Updated winnings percentage to {}: {}", percentage, saved.getId());
    }

    public Long getP2pMinPricePerTicket() {
        LotteryConfiguration config = getConfiguration();
        return config.getP2pMinPricePerTicket() != null
                ? config.getP2pMinPricePerTicket()
                : DEFAULT_P2P_MIN_PRICE_PER_TICKET;
    }

    public BigDecimal getP2pFeePercentage() {
        LotteryConfiguration config = getConfiguration();
        return config.getP2pFeePercentage() != null
                ? config.getP2pFeePercentage()
                : DEFAULT_P2P_FEE_PERCENTAGE;
    }

    @Transactional
    public void setP2pSettings(Long minPricePerTicket, BigDecimal feePercentage) {
        if (minPricePerTicket == null || minPricePerTicket < 1) {
            throw new IllegalArgumentException("P2P min price per ticket must be at least 1");
        }
        if (feePercentage == null || feePercentage.compareTo(BigDecimal.ZERO) < 0
                || feePercentage.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("P2P fee percentage must be between 0 and 1");
        }
        LotteryConfiguration current = getConfiguration();
        LotteryConfiguration config = copyConfig(current);
        config.setP2pMinPricePerTicket(minPricePerTicket);
        config.setP2pFeePercentage(feePercentage);
        config.setCreatedAt(LocalDateTime.now(ZoneId.of("GMT+5")));
        LotteryConfiguration saved = configurationRepository.save(config);
        logger.info("Updated lottery P2P settings minPrice={}, fee={}: {}",
                minPricePerTicket, feePercentage, saved.getId());
    }
}
