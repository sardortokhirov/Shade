package com.example.shade.service;

import com.example.shade.model.DailyUserStats;
import com.example.shade.repository.DailyUserStatsRepository;
import com.example.shade.repository.PromoAllowedChatRepository;
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
    private final UserLimitIncreaseService userLimitIncreaseService;
    private final FeatureService featureService;
    private final PromoAllowedChatRepository promoAllowedChatRepository;
    private final PromoWhitelistService promoWhitelistService;
    private static final ZoneId GMT_PLUS_5 = ZoneId.of("GMT+5");

    /**
     * Gets today's date in GMT+5 timezone
     */
    private LocalDate getTodayInGmtPlus5() {
        return LocalDate.now(GMT_PLUS_5);
    }

    /**
     * Gets the base daily limit for a user: system dailyBonusTransferLimit * (user percentage / 100).
     * Default percentage is 100 when not set.
     */
    public Long getBaseDailyLimitForUser(Long chatId) {
        long systemLimit = configurationService.getDailyBonusTransferLimit();
        int percentage = userLimitIncreaseService.getBaseDailyLimitPercentage(chatId);
        return (systemLimit * percentage) / 100;
    }

    /**
     * Calculates carryover amount from yesterday's unused limit
     * Carryover = unused limit (effectiveDailyLimit - dailyTransfers), capped at available limit before transfers
     * When Pay Toggle + Promo are ON and user is promo registered, deposits are not required
     */
    private Long calculateCarryoverFromYesterday(Long chatId) {
        LocalDate yesterday = getTodayInGmtPlus5().minusDays(1);
        java.util.Optional<DailyUserStats> yesterdayStats = statsRepository.findByChatIdAndDate(chatId, yesterday);
        
        if (yesterdayStats.isEmpty()) {
            return 0L;
        }
        
        DailyUserStats stats = yesterdayStats.get();
        
        // Calculate yesterday's effective daily limit (DL + TD + LD)
        Long dailyLimit = getBaseDailyLimitForUser(chatId);
        BigDecimal permanentIncrease = userLimitIncreaseService.getPermanentLimitIncrease(chatId);
        Long permanentIncreaseLong = permanentIncrease.setScale(0, java.math.RoundingMode.HALF_UP).longValue();
        BigDecimal dailyLimitIncreaseBD = stats.getDailyLimitIncrease() != null ? stats.getDailyLimitIncrease() : BigDecimal.ZERO;
        Long dailyLimitIncreaseLong = dailyLimitIncreaseBD.setScale(0, java.math.RoundingMode.HALF_UP).longValue();
        Long effectiveDailyLimit = dailyLimit + permanentIncreaseLong + dailyLimitIncreaseLong;
        
        // Check if user qualifies for deposit-free limit (pay toggle + promo + registered)
        boolean payToggleEnabled = featureService.isPayToggleEnabled();
        boolean promoEnabled = featureService.isPromoEnabled();
        boolean isPromoUser = promoAllowedChatRepository.existsByChatId(chatId);
        
        Long availableLimitBeforeTransfers;
        if (payToggleEnabled && promoEnabled && isPromoUser) {
            // Promo users with pay toggle ON: full effective limit without deposit constraint
            availableLimitBeforeTransfers = effectiveDailyLimit;
        } else {
            // Normal users: constrained by deposits + carryover
            Long dailyTopUps = stats.getDailyTopUpAmount() != null ? stats.getDailyTopUpAmount() : 0L;
            Long yesterdaysCarryover = stats.getCarryoverAmount() != null ? stats.getCarryoverAmount() : 0L;
            availableLimitBeforeTransfers = Math.min(effectiveDailyLimit, dailyTopUps + yesterdaysCarryover);
        }
        
        // Calculate unused limit (when promo is off, do not count yesterday's promo transfers as consumed)
        Long dailyTransfers = stats.getDailyTransferAmount() != null ? stats.getDailyTransferAmount() : 0L;
        Long dailyPromoTransferAmount = stats.getDailyPromoTransferAmount() != null ? stats.getDailyPromoTransferAmount() : 0L;
        long effectiveConsumedYesterday = Math.max(0L, dailyTransfers - dailyPromoTransferAmount);
        Long unusedLimit = effectiveDailyLimit - effectiveConsumedYesterday;
        
        // Carryover = unused limit, but capped at available limit before transfers
        Long carryover = Math.max(0L, Math.min(unusedLimit, availableLimitBeforeTransfers));
        
        logger.info("Calculated carryover for chatId {}: {} (unused: {}, available before transfers: {}, promoMode: {})", 
                chatId, carryover, unusedLimit, availableLimitBeforeTransfers, (payToggleEnabled && promoEnabled && isPromoUser));
        
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
                            .dailyLimitIncrease(BigDecimal.ZERO)
                            .carryoverAmount(carryover)
                            .dailyPromoTransferAmount(0L)
                            .lastUpdated(LocalDateTime.now(GMT_PLUS_5))
                            .build();
                    return statsRepository.save(stats);
                });
    }

    /**
     * Whether a deposit should grow the user's bonus limit (permanent TD + daily LD).
     * Promo OFF → yes for every platform ID. Promo ON → only linked promo_platform_link IDs.
     * Wallet withdraw quota must not use this gate.
     */
    public boolean shouldIncreaseBonusLimitOnDeposit(Long chatId, String platformUserId) {
        if (!featureService.isPromoEnabled()) {
            return true;
        }
        boolean linked = promoWhitelistService.isPromoLinkAllowed(chatId, platformUserId);
        if (!linked) {
            logger.info("Bonus limit increase blocked: promo ON and platformUserId '{}' not linked for chatId {}",
                    platformUserId, chatId);
        }
        return linked;
    }

    /**
     * Adds top-up amount to today's stats (called when top-up is confirmed).
     * Always tracks deposit amount / carryover. Bonus limit increases follow
     * {@link #shouldIncreaseBonusLimitOnDeposit(Long, String)}.
     */
    @Transactional
    public void addTopUpAmount(Long chatId, Long amount) {
        addTopUpAmount(chatId, amount, null);
    }

    /**
     * @param platformUserId destination platform account ID; used when promo mode gates limit increases
     */
    @Transactional
    public void addTopUpAmount(Long chatId, Long amount, String platformUserId) {
        DailyUserStats stats = getOrCreateTodayStats(chatId);
        stats.setDailyTopUpAmount(stats.getDailyTopUpAmount() + amount);

        Long currentCarryover = stats.getCarryoverAmount() != null ? stats.getCarryoverAmount() : 0L;
        Long newCarryover = Math.max(0L, currentCarryover - amount);
        stats.setCarryoverAmount(newCarryover);

        boolean increaseBonusLimit = shouldIncreaseBonusLimitOnDeposit(chatId, platformUserId);
        if (increaseBonusLimit) {
            BigDecimal percentage = configurationService.getTopUpDailyLimitIncreasePercentage();
            logger.info("Top-up limit increase percentage for chatId {}: {}% (amount: {})",
                    chatId, percentage, amount);

            if (percentage.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal increaseAmount = BigDecimal.valueOf(amount)
                        .multiply(percentage)
                        .divide(BigDecimal.valueOf(100), 8, java.math.RoundingMode.HALF_UP);
                userLimitIncreaseService.addPermanentLimitIncrease(chatId, increaseAmount);
                logger.info("Added permanent limit increase {} ({}% of {}) for chatId {}",
                        increaseAmount, percentage, amount, chatId);
            } else {
                logger.warn("Permanent limit increase skipped for chatId {}: topUpDailyLimitIncreasePercentage is 0% or not configured. "
                        + "To enable permanent limit increases, set topUpDailyLimitIncreasePercentage > 0 in system configuration.", chatId);
            }

            BigDecimal dailyPercentage = configurationService.getDepositDailyLimitIncreasePercentage();
            if (dailyPercentage.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal dailyIncrease = BigDecimal.valueOf(amount)
                        .multiply(dailyPercentage)
                        .divide(BigDecimal.valueOf(100), 8, java.math.RoundingMode.HALF_UP);
                BigDecimal currentDailyIncrease = stats.getDailyLimitIncrease() != null ? stats.getDailyLimitIncrease() : BigDecimal.ZERO;
                BigDecimal newDailyIncrease = currentDailyIncrease.add(dailyIncrease);
                stats.setDailyLimitIncrease(newDailyIncrease);
                logger.info("Added daily limit increase {} ({}% of {}) for chatId {}. Total daily increase: {}",
                        dailyIncrease.toPlainString(), dailyPercentage, amount, chatId, newDailyIncrease.toPlainString());
            }
        } else {
            logger.info("Skipped bonus limit increase for chatId {} amount {} (promo ON, unlinked platformUserId={})",
                    chatId, amount, platformUserId);
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

        // If transfer was made while promo was on, track it so we don't count it after promo is turned off
        boolean payToggleEnabled = featureService.isPayToggleEnabled();
        boolean promoEnabled = featureService.isPromoEnabled();
        boolean isPromoUser = promoAllowedChatRepository.existsByChatId(chatId);
        if (payToggleEnabled && promoEnabled && isPromoUser) {
            long currentPromo = stats.getDailyPromoTransferAmount() != null ? stats.getDailyPromoTransferAmount() : 0L;
            stats.setDailyPromoTransferAmount(currentPromo + amount);
        }
        
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

        // If the reverted transfer was counted as promo, reduce promo amount so we don't double-count
        long currentPromo = stats.getDailyPromoTransferAmount() != null ? stats.getDailyPromoTransferAmount() : 0L;
        stats.setDailyPromoTransferAmount(Math.max(0L, currentPromo - amount));
        
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
     * Calculates available limit based on deposits (always deposit-based):
     * 
     * availableLimit = min(effectiveDailyLimit, dailyTopUpAmount + carryover) - dailyTransfers
     * 
     * This ensures:
     * - 0 when no deposits are made today (and no carryover)
     * - Carryover from previous day can be used
     * - Limit never exceeds effectiveDailyLimit
     * 
     * Where:
     * - effectiveDailyLimit = DL + TD + LD (base + permanent increase + daily increase)
     * - carryover = temporary limit from previous day's unused limit
     * - dailyTopUpAmount = deposits made today
     * - dailyTransfers = amount already transferred today
     * 
     * Note: permanentIncrease comes from top-ups (never resets)
     *       carryover is temporary and consumed first when user transfers
     *       When Pay Toggle + Promo are ON and user is promo registered, deposits are not required
     */
    public Long getAvailableLimit(Long chatId) {
        DailyUserStats stats = getOrCreateTodayStats(chatId);
        Long dailyLimit = getBaseDailyLimitForUser(chatId);
        BigDecimal permanentIncrease = userLimitIncreaseService.getPermanentLimitIncrease(chatId);
        Long permanentIncreaseLong = permanentIncrease.setScale(0, java.math.RoundingMode.HALF_UP).longValue();
        Long carryover = stats.getCarryoverAmount() != null ? stats.getCarryoverAmount() : 0L;
        Long dailyTransfers = stats.getDailyTransferAmount() != null ? stats.getDailyTransferAmount() : 0L;
        Long dailyTopUpAmount = stats.getDailyTopUpAmount() != null ? stats.getDailyTopUpAmount() : 0L;
        
        // Calculate effective daily limit (base + permanent + daily increase)
        BigDecimal dailyLimitIncreaseBD = stats.getDailyLimitIncrease() != null ? stats.getDailyLimitIncrease() : BigDecimal.ZERO;
        Long dailyLimitIncreaseLong = dailyLimitIncreaseBD.setScale(0, java.math.RoundingMode.HALF_UP).longValue();
        Long effectiveDailyLimit = dailyLimit + permanentIncreaseLong + dailyLimitIncreaseLong;
        
        // Check if user qualifies for deposit-free limit (pay toggle + promo + registered)
        boolean payToggleEnabled = featureService.isPayToggleEnabled();
        boolean promoEnabled = featureService.isPromoEnabled();
        boolean isPromoUser = promoAllowedChatRepository.existsByChatId(chatId);
        
        Long dailyPromoTransferAmount = stats.getDailyPromoTransferAmount() != null ? stats.getDailyPromoTransferAmount() : 0L;
        Long available;
        if (payToggleEnabled && promoEnabled && isPromoUser) {
            // Promo users with pay toggle ON can transfer up to effective limit without deposits
            available = effectiveDailyLimit - dailyTransfers;
            logger.debug("getAvailableLimit for chatId {} (PROMO MODE): effectiveLimit={}, dailyTransfers={}, available={}", 
                    chatId, effectiveDailyLimit, dailyTransfers, available);
        } else {
            // Normal users: constrained by deposits + carryover. Do not count promo transfers after promo is off.
            long effectiveConsumed = Math.max(0L, dailyTransfers - dailyPromoTransferAmount);
            available = Math.min(effectiveDailyLimit, dailyTopUpAmount + carryover) - effectiveConsumed;
            logger.debug("getAvailableLimit for chatId {}: effectiveLimit={}, dailyTopUps={}, carryover={}, dailyTransfers={}, promoTransfers={}, effectiveConsumed={}, available={}", 
                    chatId, effectiveDailyLimit, dailyTopUpAmount, carryover, dailyTransfers, dailyPromoTransferAmount, effectiveConsumed, available);
        }
        
        return Math.max(0L, available); // Ensure non-negative
    }

    /**
     * Gets available limit without creating stats (read-only version)
     * When Pay Toggle + Promo are ON and user is promo registered, deposits are not required
     */
    public Long getAvailableLimitReadOnly(Long chatId) {
        LocalDate today = getTodayInGmtPlus5();
        DailyUserStats stats = statsRepository.findByChatIdAndDate(chatId, today).orElse(null);
        Long dailyLimit = getBaseDailyLimitForUser(chatId);
        BigDecimal permanentIncrease = userLimitIncreaseService.getPermanentLimitIncrease(chatId);
        Long permanentIncreaseLong = permanentIncrease.setScale(0, java.math.RoundingMode.HALF_UP).longValue();
        Long carryover = (stats != null && stats.getCarryoverAmount() != null) ? stats.getCarryoverAmount() : 0L;
        Long dailyTransfers = (stats != null && stats.getDailyTransferAmount() != null) ? stats.getDailyTransferAmount() : 0L;
        Long dailyTopUpAmount = (stats != null && stats.getDailyTopUpAmount() != null) ? stats.getDailyTopUpAmount() : 0L;
        BigDecimal dailyLimitIncreaseBD = (stats != null && stats.getDailyLimitIncrease() != null) ? stats.getDailyLimitIncrease() : BigDecimal.ZERO;
        Long dailyLimitIncreaseLong = dailyLimitIncreaseBD.setScale(0, java.math.RoundingMode.HALF_UP).longValue();
        
        // Calculate effective daily limit (base + permanent + daily increase)
        Long effectiveDailyLimit = dailyLimit + permanentIncreaseLong + dailyLimitIncreaseLong;
        
        // Check if user qualifies for deposit-free limit (pay toggle + promo + registered)
        boolean payToggleEnabled = featureService.isPayToggleEnabled();
        boolean promoEnabled = featureService.isPromoEnabled();
        boolean isPromoUser = promoAllowedChatRepository.existsByChatId(chatId);
        
        Long dailyPromoTransferAmount = (stats != null && stats.getDailyPromoTransferAmount() != null) ? stats.getDailyPromoTransferAmount() : 0L;
        Long available;
        if (payToggleEnabled && promoEnabled && isPromoUser) {
            // Promo users with pay toggle ON can transfer up to effective limit without deposits
            available = effectiveDailyLimit - dailyTransfers;
        } else {
            // Normal users: constrained by deposits + carryover. Do not count promo transfers after promo is off.
            long effectiveConsumed = Math.max(0L, dailyTransfers - dailyPromoTransferAmount);
            available = Math.min(effectiveDailyLimit, dailyTopUpAmount + carryover) - effectiveConsumed;
        }
        
        return Math.max(0L, available); // Ensure non-negative
    }

    /**
     * Gets tomorrow's permanent limit (base limit + permanent increase)
     * This is what the user will have as minimum limit tomorrow
     */
    public Long getTomorrowPermanentLimit(Long chatId) {
        Long dailyLimit = getBaseDailyLimitForUser(chatId);
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
        Long dailyLimit = getBaseDailyLimitForUser(chatId);
        BigDecimal permanentIncrease = userLimitIncreaseService.getPermanentLimitIncrease(chatId);
        Long permanentIncreaseLong = permanentIncrease.setScale(0, java.math.RoundingMode.HALF_UP).longValue();
        BigDecimal dailyLimitIncreaseBD = stats.getDailyLimitIncrease() != null ? stats.getDailyLimitIncrease() : BigDecimal.ZERO;
        Long dailyLimitIncreaseLong = dailyLimitIncreaseBD.setScale(0, java.math.RoundingMode.HALF_UP).longValue();
        
        Long result = dailyLimit + permanentIncreaseLong + dailyLimitIncreaseLong;
        
        logger.debug("getEffectiveDailyLimit for chatId {}: dailyLimit={}, permanentIncrease={}, dailyLimitIncrease={}, result={}", 
                chatId, dailyLimit, permanentIncreaseLong, dailyLimitIncreaseBD.toPlainString(), result);
        
        return result;
    }

    /**
     * Gets the effective daily limit without creating stats (read-only version)
     */
    public Long getEffectiveDailyLimitReadOnly(Long chatId) {
        LocalDate today = getTodayInGmtPlus5();
        DailyUserStats stats = statsRepository.findByChatIdAndDate(chatId, today).orElse(null);
        Long dailyLimit = getBaseDailyLimitForUser(chatId);
        BigDecimal permanentIncrease = userLimitIncreaseService.getPermanentLimitIncrease(chatId);
        Long permanentIncreaseLong = permanentIncrease.setScale(0, java.math.RoundingMode.HALF_UP).longValue();
        BigDecimal dailyLimitIncreaseBD = (stats != null && stats.getDailyLimitIncrease() != null) ? stats.getDailyLimitIncrease() : BigDecimal.ZERO;
        Long dailyLimitIncreaseLong = dailyLimitIncreaseBD.setScale(0, java.math.RoundingMode.HALF_UP).longValue();
        return dailyLimit + permanentIncreaseLong + dailyLimitIncreaseLong;
    }

    /**
     * Adds lottery winnings limit increase to today's stats
     * This adds a fixed amount to dailyLimitIncrease (unlike addTopUpAmount which calculates percentage)
     */
    @Transactional
    public void addLotteryWinningsLimitIncrease(Long chatId, Long amount) {
        DailyUserStats stats = getOrCreateTodayStats(chatId);
        BigDecimal currentIncrease = stats.getDailyLimitIncrease() != null ? stats.getDailyLimitIncrease() : BigDecimal.ZERO;
        BigDecimal newIncrease = currentIncrease.add(BigDecimal.valueOf(amount));
        stats.setDailyLimitIncrease(newIncrease);
        stats.setLastUpdated(LocalDateTime.now(GMT_PLUS_5));
        statsRepository.save(stats);
        logger.info("Added lottery winnings limit increase {} for chatId {} on date {}. Total: {}", 
                amount, chatId, stats.getDate(), newIncrease.toPlainString());
    }
}
