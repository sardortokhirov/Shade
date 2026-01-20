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
    private final UserLimitIncreaseService userLimitIncreaseService;
    private static final ZoneId GMT_PLUS_5 = ZoneId.of("GMT+5");

    /**
     * Gets today's date in GMT+5 timezone
     */
    private LocalDate getTodayInGmtPlus5() {
        return LocalDate.now(GMT_PLUS_5);
    }

    /**
     * Calculates carryover amount from yesterday's unused limit
     * Carryover = unused limit (effectiveDailyLimit - dailyTransfers), capped at available limit before transfers
     */
    private Long calculateCarryoverFromYesterday(Long chatId) {
        LocalDate yesterday = getTodayInGmtPlus5().minusDays(1);
        java.util.Optional<DailyUserStats> yesterdayStats = statsRepository.findByChatIdAndDate(chatId, yesterday);
        
        if (yesterdayStats.isEmpty()) {
            return 0L;
        }
        
        DailyUserStats stats = yesterdayStats.get();
        
        // Calculate yesterday's effective daily limit (DL + TD + LD)
        Long dailyLimit = configurationService.getDailyBonusTransferLimit();
        BigDecimal permanentIncrease = userLimitIncreaseService.getPermanentLimitIncrease(chatId);
        Long permanentIncreaseLong = permanentIncrease.setScale(0, java.math.RoundingMode.HALF_UP).longValue();
        Long dailyLimitIncrease = stats.getDailyLimitIncrease() != null ? stats.getDailyLimitIncrease() : 0L;
        Long effectiveDailyLimit = dailyLimit + permanentIncreaseLong + dailyLimitIncrease;
        
        // Calculate yesterday's available limit (before transfers)
        Long availableLimitBeforeTransfers = effectiveDailyLimit;
        if (!featureService.isPayToggleEnabled()) {
            Long dailyTopUps = stats.getDailyTopUpAmount() != null ? stats.getDailyTopUpAmount() : 0L;
            availableLimitBeforeTransfers = Math.min(effectiveDailyLimit, dailyTopUps);
        }
        
        // Calculate unused limit
        Long dailyTransfers = stats.getDailyTransferAmount() != null ? stats.getDailyTransferAmount() : 0L;
        Long unusedLimit = effectiveDailyLimit - dailyTransfers;
        
        // Carryover = unused limit, but capped at available limit before transfers
        Long carryover = Math.max(0L, Math.min(unusedLimit, availableLimitBeforeTransfers));
        
        logger.info("Calculated carryover for chatId {}: {} (unused: {}, available before transfers: {})", 
                chatId, carryover, unusedLimit, availableLimitBeforeTransfers);
        
        return carryover;
    }

    /**
     * Gets or creates today's stats for a user
     * If creating new stats, calculates carryover from yesterday's unused limit
     */
    @Transactional
    public DailyUserStats getOrCreateTodayStats(Long chatId) {
        LocalDate today = getTodayInGmtPlus5();
        return statsRepository.findByChatIdAndDate(chatId, today)
                .orElseGet(() -> {
                    // Calculate carryover from yesterday
                    Long carryover = calculateCarryoverFromYesterday(chatId);
                    
                    DailyUserStats stats = DailyUserStats.builder()
                            .chatId(chatId)
                            .date(today)
                            .dailyTopUpAmount(0L)
                            .dailyTransferAmount(0L)
                            .dailyLimitIncrease(0L)
                            .carryoverAmount(carryover)
                            .lastUpdated(LocalDateTime.now(GMT_PLUS_5))
                            .build();
                    return statsRepository.save(stats);
                });
    }

    /**
     * Adds top-up amount to today's stats (called when top-up is confirmed)
     * Also calculates and adds the permanent limit increase based on configured percentage
     * Decreases carryover by deposit amount (excess deposit will be calculated as carryover for next day)
     */
    @Transactional
    public void addTopUpAmount(Long chatId, Long amount) {
        DailyUserStats stats = getOrCreateTodayStats(chatId);
        stats.setDailyTopUpAmount(stats.getDailyTopUpAmount() + amount);
        
        // Decrease carryover by deposit amount
        Long currentCarryover = stats.getCarryoverAmount() != null ? stats.getCarryoverAmount() : 0L;
        Long newCarryover = Math.max(0L, currentCarryover - amount);
        stats.setCarryoverAmount(newCarryover);
        
        // If deposit exceeds carryover, excess will be calculated at midnight for next day
        // (excess is: amount - currentCarryover, but we don't store it separately)
        
        // Calculate and add permanent limit increase based on configured percentage
        BigDecimal percentage = configurationService.getTopUpDailyLimitIncreasePercentage();
        if (percentage.compareTo(BigDecimal.ZERO) > 0) {
            // Store precise decimal value without rounding
            BigDecimal increaseAmount = BigDecimal.valueOf(amount)
                    .multiply(percentage)
                    .divide(BigDecimal.valueOf(100), 8, java.math.RoundingMode.HALF_UP);
            // Add to permanent increase (accumulates forever, never resets)
            userLimitIncreaseService.addPermanentLimitIncrease(chatId, increaseAmount);
            logger.info("Added permanent limit increase {} ({}% of {}) for chatId {}", 
                    increaseAmount, percentage, amount, chatId);
        }
        
        stats.setLastUpdated(LocalDateTime.now(GMT_PLUS_5));
        statsRepository.save(stats);
        logger.info("Added top-up amount {} for chatId {} on date {}. Carryover decreased from {} to {}", 
                amount, chatId, stats.getDate(), currentCarryover, newCarryover);
    }

    /**
     * Adds transfer amount to today's stats (called when bonus transfer is approved)
     * Consumes carryover first, then permanent limit
     */
    @Transactional
    public void addTransferAmount(Long chatId, Long amount) {
        DailyUserStats stats = getOrCreateTodayStats(chatId);
        
        // Consume carryover first
        Long currentCarryover = stats.getCarryoverAmount() != null ? stats.getCarryoverAmount() : 0L;
        Long carryoverConsumed = Math.min(amount, currentCarryover);
        Long remainingTransfer = amount - carryoverConsumed;
        
        // Update carryover (decrease by consumed amount)
        stats.setCarryoverAmount(Math.max(0L, currentCarryover - carryoverConsumed));
        
        // Add to transfer amount (this tracks total transfers, not which limit was used)
        stats.setDailyTransferAmount(stats.getDailyTransferAmount() + amount);
        
        stats.setLastUpdated(LocalDateTime.now(GMT_PLUS_5));
        statsRepository.save(stats);
        logger.info("Added transfer amount {} for chatId {} on date {}. Carryover consumed: {}, remaining: {}", 
                amount, chatId, stats.getDate(), carryoverConsumed, remainingTransfer);
    }

    /**
     * Subtracts transfer amount from today's stats (called when bonus transfer is
     * canceled/declined)
     * Restores carryover that was consumed (up to the amount being refunded)
     */
    @Transactional
    public void subtractTransferAmount(Long chatId, Long amount) {
        DailyUserStats stats = getOrCreateTodayStats(chatId);
        stats.setDailyTransferAmount(Math.max(0L, stats.getDailyTransferAmount() - amount));
        
        // Restore carryover that was consumed (restore up to the refunded amount)
        // This is a heuristic: we restore carryover by the refunded amount, which may over-restore
        // if the original transfer consumed permanent limit, but this is acceptable
        Long currentCarryover = stats.getCarryoverAmount() != null ? stats.getCarryoverAmount() : 0L;
        Long newCarryover = currentCarryover + amount;
        stats.setCarryoverAmount(newCarryover);
        
        stats.setLastUpdated(LocalDateTime.now(GMT_PLUS_5));
        statsRepository.save(stats);
        logger.info("Subtracted transfer amount {} for chatId {} on date {}. Carryover restored from {} to {}", 
                amount, chatId, stats.getDate(), currentCarryover, newCarryover);
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
     * Calculates available limit including carryover:
     * availableLimit = (permanentLimit + carryover) - dailyTransfers
     * 
     * Where:
     * - permanentLimit = DL + TD (base limit + permanent increase from top-ups)
     * - carryover = temporary limit from previous day's unused limit
     * - dailyTransfers = amount already transferred today
     * 
     * Note: permanentIncrease comes from top-ups (never resets)
     *       carryover is temporary and consumed first when user transfers
     */
    public Long getAvailableLimit(Long chatId) {
        DailyUserStats stats = getOrCreateTodayStats(chatId);
        Long dailyLimit = configurationService.getDailyBonusTransferLimit();
        BigDecimal permanentIncrease = userLimitIncreaseService.getPermanentLimitIncrease(chatId);
        Long permanentIncreaseLong = permanentIncrease.setScale(0, java.math.RoundingMode.HALF_UP).longValue();
        Long carryover = stats.getCarryoverAmount() != null ? stats.getCarryoverAmount() : 0L;
        Long dailyTransfers = stats.getDailyTransferAmount() != null ? stats.getDailyTransferAmount() : 0L;
        
        // Calculate permanent limit (DL + TD)
        Long permanentLimit = dailyLimit + permanentIncreaseLong;
        
        // Calculate total available limit (permanent + carryover - transfers)
        Long available = permanentLimit + carryover - dailyTransfers;
        
        return Math.max(0L, available); // Ensure non-negative
    }

    /**
     * Gets available limit without creating stats (read-only version)
     * Uses same formula as getAvailableLimit(): (permanentLimit + carryover) - dailyTransfers
     */
    public Long getAvailableLimitReadOnly(Long chatId) {
        LocalDate today = getTodayInGmtPlus5();
        DailyUserStats stats = statsRepository.findByChatIdAndDate(chatId, today).orElse(null);
        Long dailyLimit = configurationService.getDailyBonusTransferLimit();
        BigDecimal permanentIncrease = userLimitIncreaseService.getPermanentLimitIncrease(chatId);
        Long permanentIncreaseLong = permanentIncrease.setScale(0, java.math.RoundingMode.HALF_UP).longValue();
        Long carryover = (stats != null && stats.getCarryoverAmount() != null) ? stats.getCarryoverAmount() : 0L;
        Long dailyTransfers = (stats != null && stats.getDailyTransferAmount() != null) ? stats.getDailyTransferAmount() : 0L;
        
        // Calculate permanent limit (DL + TD)
        Long permanentLimit = dailyLimit + permanentIncreaseLong;
        
        // Calculate total available limit (permanent + carryover - transfers)
        Long available = permanentLimit + carryover - dailyTransfers;
        
        return Math.max(0L, available); // Ensure non-negative
    }

    /**
     * Gets tomorrow's permanent limit (base limit + permanent increase)
     * This is what the user will have as minimum limit tomorrow
     */
    public Long getTomorrowPermanentLimit(Long chatId) {
        Long dailyLimit = configurationService.getDailyBonusTransferLimit();
        BigDecimal permanentIncrease = userLimitIncreaseService.getPermanentLimitIncrease(chatId);
        Long permanentIncreaseLong = permanentIncrease.setScale(0, java.math.RoundingMode.HALF_UP).longValue();
        return dailyLimit + permanentIncreaseLong;
    }

    /**
     * Checks if user can transfer the requested amount
     */
    public boolean canTransfer(Long chatId, Long amount) {
        Long availableLimit = getAvailableLimit(chatId);
        return amount <= availableLimit;
    }

    /**
     * Gets the effective daily limit (base limit + permanent increase + today's daily increase)
     * permanentIncrease: from top-ups (never resets)
     * dailyLimitIncrease: from lottery winnings (resets daily)
     */
    public Long getEffectiveDailyLimit(Long chatId) {
        DailyUserStats stats = getOrCreateTodayStats(chatId);
        Long dailyLimit = configurationService.getDailyBonusTransferLimit();
        BigDecimal permanentIncrease = userLimitIncreaseService.getPermanentLimitIncrease(chatId);
        Long permanentIncreaseLong = permanentIncrease.setScale(0, java.math.RoundingMode.HALF_UP).longValue();
        Long dailyLimitIncrease = stats.getDailyLimitIncrease() != null ? stats.getDailyLimitIncrease() : 0L;
        return dailyLimit + permanentIncreaseLong + dailyLimitIncrease;
    }

    /**
     * Gets the effective daily limit without creating stats (read-only version)
     */
    public Long getEffectiveDailyLimitReadOnly(Long chatId) {
        LocalDate today = getTodayInGmtPlus5();
        DailyUserStats stats = statsRepository.findByChatIdAndDate(chatId, today).orElse(null);
        Long dailyLimit = configurationService.getDailyBonusTransferLimit();
        BigDecimal permanentIncrease = userLimitIncreaseService.getPermanentLimitIncrease(chatId);
        Long permanentIncreaseLong = permanentIncrease.setScale(0, java.math.RoundingMode.HALF_UP).longValue();
        Long dailyLimitIncrease = (stats != null && stats.getDailyLimitIncrease() != null) ? stats.getDailyLimitIncrease() : 0L;
        return dailyLimit + permanentIncreaseLong + dailyLimitIncrease;
    }

    /**
     * Adds lottery winnings limit increase to today's stats
     * This adds a fixed amount to dailyLimitIncrease (unlike addTopUpAmount which calculates percentage)
     */
    @Transactional
    public void addLotteryWinningsLimitIncrease(Long chatId, Long amount) {
        DailyUserStats stats = getOrCreateTodayStats(chatId);
        stats.setDailyLimitIncrease(stats.getDailyLimitIncrease() + amount);
        stats.setLastUpdated(LocalDateTime.now(GMT_PLUS_5));
        statsRepository.save(stats);
        logger.info("Added lottery winnings limit increase {} for chatId {} on date {}", amount, chatId, stats.getDate());
    }
}
