package com.example.shade.service;

import com.example.shade.model.UserLimitIncrease;
import com.example.shade.repository.UserLimitIncreaseRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class UserLimitIncreaseService {
    private static final Logger logger = LoggerFactory.getLogger(UserLimitIncreaseService.class);
    private final UserLimitIncreaseRepository repository;
    private static final ZoneId GMT_PLUS_5 = ZoneId.of("GMT+5");

    /**
     * Get accumulated permanent limit increase for a user
     */
    public Long getPermanentLimitIncrease(Long chatId) {
        return repository.findByChatId(chatId)
                .map(UserLimitIncrease::getAccumulatedLimitIncrease)
                .orElse(0L);
    }

    /**
     * Add amount to permanent limit increase (accumulates forever)
     * This method only adds to the limit - it never decreases it
     */
    @Transactional
    public void addPermanentLimitIncrease(Long chatId, Long amount) {
        if (amount == null || amount < 0) {
            throw new IllegalArgumentException("Amount to add must be non-negative");
        }
        
        UserLimitIncrease limitIncrease = getOrCreate(chatId);
        Long oldLimit = limitIncrease.getAccumulatedLimitIncrease();
        Long newLimit = oldLimit + amount;
        
        limitIncrease.setAccumulatedLimitIncrease(newLimit);
        limitIncrease.setLastUpdated(LocalDateTime.now(GMT_PLUS_5));
        repository.save(limitIncrease);
        
        logger.info("Added permanent limit increase {} for chatId {}. Old: {}, New: {}", 
                amount, chatId, oldLimit, newLimit);
    }
    
    /**
     * Reset permanent limit increase to 0
     * This method requires explicit confirmation to prevent accidental resets
     * 
     * @param chatId User's chat ID
     * @param confirmReset Must be true to confirm the reset operation
     * @throws IllegalArgumentException if confirmReset is not true
     */
    @Transactional
    public void resetLimit(Long chatId, boolean confirmReset) {
        if (!confirmReset) {
            throw new IllegalArgumentException("Reset confirmation required. " +
                    "Permanent limit increases should never be reset automatically. " +
                    "Set confirmReset=true if this is intentional.");
        }
        
        UserLimitIncrease limitIncrease = getOrCreate(chatId);
        Long oldLimit = limitIncrease.getAccumulatedLimitIncrease();
        
        if (oldLimit == 0) {
            logger.info("Reset limit requested for chatId {} but limit is already 0", chatId);
            return;
        }
        
        logger.warn("RESETTING PERMANENT LIMIT - chatId: {}, old limit: {}, new limit: 0. " +
                "This is a destructive operation.", chatId, oldLimit);
        
        limitIncrease.setAccumulatedLimitIncrease(0L);
        limitIncrease.setLastUpdated(LocalDateTime.now(GMT_PLUS_5));
        repository.save(limitIncrease);
        
        logger.error("PERMANENT LIMIT RESET COMPLETED - chatId: {}, previous value: {} was reset to 0", 
                chatId, oldLimit);
    }

    /**
     * Get or create UserLimitIncrease record for a user
     */
    @Transactional
    public UserLimitIncrease getOrCreate(Long chatId) {
        return repository.findByChatId(chatId)
                .orElseGet(() -> {
                    UserLimitIncrease newRecord = UserLimitIncrease.builder()
                            .chatId(chatId)
                            .accumulatedLimitIncrease(0L)
                            .lastUpdated(LocalDateTime.now(GMT_PLUS_5))
                            .build();
                    return repository.save(newRecord);
                });
    }
}
