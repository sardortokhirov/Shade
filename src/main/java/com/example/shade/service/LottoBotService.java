package com.example.shade.service;

import com.example.shade.bot.LottoMessageSender;
import com.example.shade.model.AdminChat;
import com.example.shade.repository.AdminChatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;

@Service
public class LottoBotService {
    private static final Logger logger = LoggerFactory.getLogger(LottoBotService.class);
    private final AdminChatRepository adminChatRepository;
    private final LottoMessageSender messageSender;
    private final LanguageSessionService languageSessionService;
    private static final Random RANDOM = new Random();

    public LottoBotService(AdminChatRepository adminChatRepository, LottoMessageSender messageSender, LanguageSessionService languageSessionService) {
        this.adminChatRepository = adminChatRepository;
        this.messageSender = messageSender;
        this.languageSessionService = languageSessionService;
    }

    public void logWin(long numberOfTickets, Long userId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.valueOf(3600)) <= 0) {
            logger.info("Win amount {} for userId {} is not greater than 3,600; no log sent", amount, userId);
            return;
        }

        String maskedUserId = userId.toString().length() >= 7
                ? userId.toString().substring(0, 3).concat("***").concat(userId.toString().substring(6))
                : userId.toString();
        String date = LocalDateTime.now(ZoneId.of("GMT+5"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String amountStr = formatWholeNumber(amount);
        String logMessage = String.format(
                languageSessionService.getTranslationUz("lotto.message.win_log"),
                numberOfTickets, amountStr, maskedUserId, date, getRandomCongratulationsUz()
        );

        List<AdminChat> adminChats = adminChatRepository.findByReceiveNotificationsTrue();
        if (adminChats.isEmpty()) {
            logger.warn("No admin channels with notifications enabled for userId {} and amount {}", userId, amount);
            return;
        }

        for (AdminChat adminChat : adminChats) {
            messageSender.sendMessage(adminChat.getChatId(), logMessage);
            logger.info("Sent win log to channel {}: {}", adminChat.getChatId(), logMessage);
        }
    }

    public void logBonusTopUpWin(Long chatId, Long amount, String platform, LocalDateTime timestamp) {
        String date = timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String maskedChatId = chatId.toString().length() >= 7
                ? chatId.toString().substring(0, 3).concat("***").concat(chatId.toString().substring(6))
                : chatId.toString();
        String logMessage = String.format(
                languageSessionService.getTranslationUz("lotto.message.bonus_topup_win"),
                amount, amount, platform, date, maskedChatId
        );

        List<AdminChat> adminChats = adminChatRepository.findByReceiveNotificationsTrue();
        if (adminChats.isEmpty()) {
            logger.warn("No admin channels with notifications enabled for bonus top-up: chatId {}, amount {}", chatId, amount);
            return;
        }

        for (AdminChat adminChat : adminChats) {
            messageSender.sendMessage(adminChat.getChatId(), logMessage);
            logger.info("Sent bonus top-up win log to channel {}: {}", adminChat.getChatId(), logMessage);
        }
    }

    /**
     * Sends a tip/donation log to all admin chats (lotto log bot).
     * User ID is masked (e.g. 175***3324). Tip/request ID is not shown.
     */
    public void logTip(Long tipId, Long chatId, Long amount, long bonusTickets) {
        String date = LocalDateTime.now(ZoneId.of("GMT+5")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String maskedUserId = maskUserId(chatId);
        String logMessage = String.format(
                "🎁 Akkaunt rivoji uchun\n💸 Summa: %,d UZS\n👤 User Id: %s\n🎟️ Bonus chiptalar: %d\n📅 Sana: %s",
                amount, maskedUserId, bonusTickets, date);

        List<AdminChat> adminChats = adminChatRepository.findByReceiveNotificationsTrue();
        if (adminChats.isEmpty()) {
            logger.warn("No admin channels with notifications enabled for tip log: chatId {}, amount {}", chatId, amount);
            return;
        }

        for (AdminChat adminChat : adminChats) {
            messageSender.sendMessage(adminChat.getChatId(), logMessage);
            logger.info("Sent tip log to lotto channel {}: {}", adminChat.getChatId(), logMessage);
        }
    }

    /** Random congratulations text always in Uzbek (for lotto log bot). */
    private String getRandomCongratulationsUz() {
        int index = RANDOM.nextInt(4) + 1;
        return languageSessionService.getTranslationUz("lotto.congratulations." + index);
    }

    /**
     * Formats BigDecimal as whole number (no decimals) for lottery win messages.
     */
    private String formatWholeNumber(BigDecimal amount) {
        if (amount == null) return "0";
        return amount.setScale(0, java.math.RoundingMode.DOWN).toPlainString();
    }

    /** Masks user/chat ID for display (e.g. 1755953324 → 175***3324). */
    private String maskUserId(Long id) {
        if (id == null) return "***";
        String s = id.toString();
        if (s.length() >= 7) {
            return s.substring(0, 3) + "***" + s.substring(s.length() - 4);
        }
        return "***";
    }
}