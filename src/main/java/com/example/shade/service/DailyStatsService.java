package com.example.shade.service;

import com.example.shade.model.DailyUserStats;
import com.example.shade.repository.DailyUserStatsRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Service for managing daily user statistics (top-ups and bonus transfers)
 */
@Service
@RequiredArgsConstructor
public class DailyStatsService {
    private static final Logger logger = LoggerFactory.getLogger(DailyStatsService.class);
    private final DailyUserStatsRepository statsRepository;
    private final SystemConfigurationService configurationService;
    private final FeatureService featureService;
    private static final ZoneId GMT_PLUS_5 = ZoneId.of("GMT+5");

    /**
     * Gets today's date in GMT+5 timezone
     */
    private LocalDate getTodayInGmtPlus5() {
        return LocalDate.now(GMT_PLUS_5);
    }

    /**
     * Gets or creates today's stats for a user
     */
    @Transactional
    public DailyUserStats getOrCreateTodayStats(Long chatId) {
        LocalDate today = getTodayInGmtPlus5();
        return statsRepository.findByChatIdAndDate(chatId, today)
                .orElseGet(() -> {
                    DailyUserStats stats = DailyUserStats.builder()
                            .chatId(chatId)
                            .date(today)
                            .dailyTopUpAmount(0L)
                            .dailyTransferAmount(0L)
                            .dailyLimitIncrease(0L)
                            .lastUpdated(LocalDateTime.now(GMT_PLUS_5))
                            .build();
                    return statsRepository.save(stats);
                });
    }

    /**
     * Adds top-up amount to today's stats (called when top-up is confirmed)
     * Also calculates and adds the daily limit increase based on configured percentage
     */
    @Transactional
    public void addTopUpAmount(Long chatId, Long amount) {
        DailyUserStats stats = getOrCreateTodayStats(chatId);
        stats.setDailyTopUpAmount(stats.getDailyTopUpAmount() + amount);
        
        // Calculate and add daily limit increase based on configured percentage
        BigDecimal percentage = configurationService.getTopUpDailyLimitIncreasePercentage();
        if (percentage.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal increaseAmount = BigDecimal.valueOf(amount)
                    .multiply(percentage)
                    .divide(BigDecimal.valueOf(100), 0, java.math.RoundingMode.HALF_UP);
            long increase = increaseAmount.longValue();
            stats.setDailyLimitIncrease(stats.getDailyLimitIncrease() + increase);
            logger.info("Added daily limit increase {} ({}% of {}) for chatId {} on date {}", 
                    increase, percentage, amount, chatId, stats.getDate());
        }
        
        stats.setLastUpdated(LocalDateTime.now(GMT_PLUS_5));
        statsRepository.save(stats);
        logger.info("Added top-up amount {} for chatId {} on date {}", amount, chatId, stats.getDate());
    }

    /**
     * Adds transfer amount to today's stats (called when bonus transfer is
     * approved)
     */
    @Transactional
    public void addTransferAmount(Long chatId, Long amount) {
        DailyUserStats stats = getOrCreateTodayStats(chatId);
        stats.setDailyTransferAmount(stats.getDailyTransferAmount() + amount);
        stats.setLastUpdated(LocalDateTime.now(GMT_PLUS_5));
        statsRepository.save(stats);
        logger.info("Added transfer amount {} for chatId {} on date {}", amount, chatId, stats.getDate());
    }

    /**
     * Subtracts transfer amount from today's stats (called when bonus transfer is
     * canceled/declined)
     */
    @Transactional
    public void subtractTransferAmount(Long chatId, Long amount) {
        DailyUserStats stats = getOrCreateTodayStats(chatId);
        stats.setDailyTransferAmount(Math.max(0L, stats.getDailyTransferAmount() - amount));
        stats.setLastUpdated(LocalDateTime.now(GMT_PLUS_5));
        statsRepository.save(stats);
        logger.info("Subtracted transfer amount {} for chatId {} on date {}", amount, chatId, stats.getDate());
    }

    /**
     * Subtracts top-up amount from today's stats (called when bonus transfer request is created)
     * This reserves the deposit amount for the pending transfer
     */
    @Transactional
    public void subtractTopUpAmount(Long chatId, Long amount) {
        DailyUserStats stats = getOrCreateTodayStats(chatId);
        stats.setDailyTopUpAmount(Math.max(0L, stats.getDailyTopUpAmount() - amount));
        stats.setLastUpdated(LocalDateTime.now(GMT_PLUS_5));
        statsRepository.save(stats);
        logger.info("Subtracted top-up amount {} for chatId {} on date {}", amount, chatId, stats.getDate());
    }

    /**
     * Calculates available limit based on Pay toggle:
     * - Pay toggle OFF: min(dailyLimit + dailyLimitIncrease, dailyTopUps) - dailyTransfers
     * - Pay toggle ON: (dailyLimit + dailyLimitIncrease) - dailyTransfers (ignores deposits)
     */
    public Long getAvailableLimit(Long chatId) {
        DailyUserStats stats = getOrCreateTodayStats(chatId);
        Long dailyLimit = configurationService.getDailyBonusTransferLimit();
        Long dailyLimitIncrease = stats.getDailyLimitIncrease() != null ? stats.getDailyLimitIncrease() : 0L;
        Long dailyTopUps = stats.getDailyTopUpAmount();
        Long dailyTransfers = stats.getDailyTransferAmount();

        // Calculate effective daily limit (base limit + increase from top-ups)
        Long effectiveDailyLimit = dailyLimit + dailyLimitIncrease;

        Long available;
        if (featureService.isPayToggleEnabled()) {
            // Pay toggle ON: ignore deposits, use full daily limit (including increase)
            available = effectiveDailyLimit - dailyTransfers;
        } else {
            // Pay toggle OFF: current behavior (minimum of effective limit and deposits)
            available = Math.min(effectiveDailyLimit, dailyTopUps) - dailyTransfers;
        }
        
        return Math.max(0L, available); // Ensure non-negative
    }

    /**
     * Checks if user can transfer the requested amount
     */
    public boolean canTransfer(Long chatId, Long amount) {
        Long availableLimit = getAvailableLimit(chatId);
        return amount <= availableLimit;
    }

    /**
     * Gets the effective daily limit (base limit + increase from top-ups)
     */
    public Long getEffectiveDailyLimit(Long chatId) {
        DailyUserStats stats = getOrCreateTodayStats(chatId);
        Long dailyLimit = configurationService.getDailyBonusTransferLimit();
        Long dailyLimitIncrease = stats.getDailyLimitIncrease() != null ? stats.getDailyLimitIncrease() : 0L;
        return dailyLimit + dailyLimitIncrease;
    }
}
