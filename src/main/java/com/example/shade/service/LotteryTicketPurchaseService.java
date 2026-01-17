package com.example.shade.service;

import com.example.shade.model.LotteryTicketPurchase;
import com.example.shade.repository.LotteryTicketPurchaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class LotteryTicketPurchaseService {
    private final LotteryTicketPurchaseRepository purchaseRepository;
    private static final ZoneId GMT_PLUS_5 = ZoneId.of("GMT+5");

    public LocalDateTime getLastPurchaseTime(Long chatId) {
        return purchaseRepository.findByChatId(chatId)
                .map(LotteryTicketPurchase::getLastPurchaseTime)
                .orElse(null);
    }

    @Transactional
    public void updatePurchaseTime(Long chatId) {
        LotteryTicketPurchase purchase = purchaseRepository.findByChatId(chatId)
                .orElse(LotteryTicketPurchase.builder()
                        .chatId(chatId)
                        .build());
        purchase.setLastPurchaseTime(LocalDateTime.now(GMT_PLUS_5));
        purchaseRepository.save(purchase);
    }

    public boolean canPurchase(Long chatId, Long cooldownSeconds) {
        LocalDateTime lastPurchase = getLastPurchaseTime(chatId);
        if (lastPurchase == null) {
            return true;
        }
        LocalDateTime now = LocalDateTime.now(GMT_PLUS_5);
        long secondsSinceLastPurchase = ChronoUnit.SECONDS.between(lastPurchase, now);
        return secondsSinceLastPurchase >= cooldownSeconds;
    }

    public long getRemainingCooldownSeconds(Long chatId, Long cooldownSeconds) {
        LocalDateTime lastPurchase = getLastPurchaseTime(chatId);
        if (lastPurchase == null) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now(GMT_PLUS_5);
        long secondsSinceLastPurchase = ChronoUnit.SECONDS.between(lastPurchase, now);
        if (secondsSinceLastPurchase >= cooldownSeconds) {
            return 0;
        }
        return cooldownSeconds - secondsSinceLastPurchase;
    }
}
