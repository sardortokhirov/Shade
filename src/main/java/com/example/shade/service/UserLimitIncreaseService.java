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
     */
    @Transactional
    public void addPermanentLimitIncrease(Long chatId, Long amount) {
        UserLimitIncrease limitIncrease = getOrCreate(chatId);
        limitIncrease.setAccumulatedLimitIncrease(
                limitIncrease.getAccumulatedLimitIncrease() + amount);
        limitIncrease.setLastUpdated(LocalDateTime.now(GMT_PLUS_5));
        repository.save(limitIncrease);
        logger.info("Added permanent limit increase {} for chatId {}. Total: {}", 
                amount, chatId, limitIncrease.getAccumulatedLimitIncrease());
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
