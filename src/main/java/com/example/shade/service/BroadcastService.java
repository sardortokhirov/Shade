package com.example.shade.service;

import com.example.shade.bot.AdminBotMessageSender;
import com.example.shade.bot.MessageSender;
import com.example.shade.bot.ShadePaymentBot;
import com.example.shade.model.BlockedUser;
import com.example.shade.repository.BlockedUserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class BroadcastService {
    private static final Logger logger = LoggerFactory.getLogger(BroadcastService.class);
    private final BlockedUserRepository blockedUserRepository;
    private final TaskScheduler taskScheduler;
    private final MessageSender messageSender;
    private final AdminBotMessageSender adminBotMessageSender;

    @Async("broadcastExecutor")
    public CompletableFuture<Void> sendBroadcast(String messageText, String parseMode, String buttonText, String buttonUrl,
                                                 LocalDateTime scheduledTime, Long adminChatId) {
        // Validate parseMode
        String effectiveParseMode = parseMode != null && parseMode.equalsIgnoreCase("HTML") ? "HTML" : null;
        if (effectiveParseMode != null && !isValidHtml(messageText)) {
            logger.warn("Invalid HTML in messageText: {}", messageText);
            throw new IllegalArgumentException("Invalid HTML content in message text.");
        }

        // Prepare the message
        InlineKeyboardMarkup markup;
        if (buttonText != null && !buttonText.trim().isEmpty() && buttonUrl != null && !buttonUrl.trim().isEmpty()) {
            markup = createButtonMarkup(buttonText, buttonUrl);
        } else {
            markup = null;
        }

        // Track success and failure counts
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // Create broadcast task
        Runnable broadcastTask = () -> {
            final int batchSize = 50;
            long totalUsers = blockedUserRepository.countByPhoneNumberNot("BLOCKED");
            final int totalBatches = Math.max(1, (int) Math.ceil((double) totalUsers / batchSize));
            logger.info("Starting broadcast for {} non-blocked users", totalUsers);

            for (int pageNumber = 0; ; pageNumber++) {
                Page<BlockedUser> page = blockedUserRepository
                        .findByPhoneNumberNot("BLOCKED", PageRequest.of(pageNumber, batchSize));
                if (page.isEmpty()) {
                    break;
                }
                processBatch(page.getContent(), messageText, effectiveParseMode, markup, successCount, failureCount);
                int currentBatch = pageNumber + 1;
                if (adminChatId != null) {
                    sendProgressUpdate(adminChatId, currentBatch, totalBatches, successCount.get(), failureCount.get());
                }
                if (page.hasNext()) {
                    try {
                        Thread.sleep(250);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        logger.warn("Broadcast interrupted");
                        break;
                    }
                }
            }
            logger.info("Broadcast completed: {} successful, {} failed", successCount.get(), failureCount.get());
            if (adminChatId != null) {
                adminBotMessageSender.sendTextMessage(adminChatId, String.format(
                        "✅ Broadcast yakunlandi!\n\n✔️ Muvaffaqiyatli: %d\n❌ Xato: %d\n📊 Jami: %d",
                        successCount.get(), failureCount.get(), totalUsers));
            }
        };

        // Schedule or execute immediately
        if (scheduledTime != null && scheduledTime.isAfter(LocalDateTime.now(ZoneId.of("GMT+5")))) {
            taskScheduler.schedule(broadcastTask, scheduledTime.atZone(ZoneId.systemDefault()).toInstant());
            logger.info("Broadcast scheduled for {}", scheduledTime);
        } else {
            broadcastTask.run(); // This method itself already runs on broadcastExecutor.
        }
        return CompletableFuture.completedFuture(null);
    }

    private void processBatch(List<BlockedUser> users, String messageText, String parseMode, InlineKeyboardMarkup markup,
                              AtomicInteger successCount, AtomicInteger failureCount) {
        for (BlockedUser user : users) {
            try {
                SendMessage message = new SendMessage();
                message.setChatId(user.getChatId());
                message.setText(messageText);
                if (parseMode != null) {
                    message.setParseMode(parseMode);
                }
                if (markup != null) {
                    message.setReplyMarkup(markup);
                }
                if (messageSender.sendMessage(message, user.getChatId())) {
                    successCount.incrementAndGet();
                } else {
                    failureCount.incrementAndGet();
                }
                pauseBetweenMessages();
            } catch (Exception e) {
                failureCount.incrementAndGet();
                logger.error("Failed to send broadcast to user {}: {}", user.getChatId(), e.getMessage());
            }
        }
    }

    private void pauseBetweenMessages() {
        try {
            // Keep this bulk path below Telegram's ~30 messages/sec global cap.
            Thread.sleep(45);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void sendProgressUpdate(Long adminChatId, int currentBatch, int totalBatches, int successCount, int failureCount) {
        adminBotMessageSender.sendTextMessage(adminChatId, String.format(
                "⏳ Broadcast: %d%%\n📦 Batch: %d/%d\n✅: %d\n❌: %d",
                (currentBatch * 100) / totalBatches, currentBatch, totalBatches, successCount, failureCount));
    }

    private InlineKeyboardMarkup createButtonMarkup(String buttonText, String buttonUrl) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(buttonText);
        button.setUrl(buttonUrl);
        rows.add(List.of(button));
        markup.setKeyboard(rows);
        return markup;
    }

    private boolean isValidHtml(String messageText) {
        // Basic validation for Telegram-supported HTML tags
        // Telegram supports: <b>, <i>, <a>, <code>, <pre>, <s>, <u>, <em>, <strong>, <tg-spoiler>, <tg-emoji>
        String[] allowedTags = {"b", "i", "a", "code", "pre", "s", "u", "em", "strong", "tg-spoiler", "tg-emoji"};
        for (String tag : allowedTags) {
            messageText = messageText.replaceAll("<" + tag + "[^>]*>", "").replaceAll("</" + tag + ">", "");
        }
        // Check if any HTML tags remain
        return !messageText.matches(".*<[a-zA-Z]+[^>]*>.*");
    }
}