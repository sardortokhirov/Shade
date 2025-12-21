package com.example.shade.service;

import com.example.shade.model.DailyUserStats;
import com.example.shade.repository.DailyUserStatsRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
                            .lastUpdated(LocalDateTime.now(GMT_PLUS_5))
                            .build();
                    return statsRepository.save(stats);
                });
    }

    /**
     * Adds top-up amount to today's stats (called when top-up is confirmed)
     */
    @Transactional
    public void addTopUpAmount(Long chatId, Long amount) {
        DailyUserStats stats = getOrCreateTodayStats(chatId);
        stats.setDailyTopUpAmount(stats.getDailyTopUpAmount() + amount);
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
     * Calculates available limit: min(dailyLimit, dailyTopUps) - dailyTransfers
     */
    public Long getAvailableLimit(Long chatId) {
        DailyUserStats stats = getOrCreateTodayStats(chatId);
        Long dailyLimit = configurationService.getDailyBonusTransferLimit();
        Long dailyTopUps = stats.getDailyTopUpAmount();
        Long dailyTransfers = stats.getDailyTransferAmount();

        Long available = Math.min(dailyLimit, dailyTopUps) - dailyTransfers;
        return Math.max(0L, available); // Ensure non-negative
    }

    /**
     * Checks if user can transfer the requested amount
     */
    public boolean canTransfer(Long chatId, Long amount) {
        Long availableLimit = getAvailableLimit(chatId);
        return amount <= availableLimit;
    }
}
